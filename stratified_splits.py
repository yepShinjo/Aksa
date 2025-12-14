# stratified_70_15_15_prioritize_train.py
import os, glob, shutil, random
from collections import defaultdict
import numpy as np

# ================== CONFIG ==================
SOURCE_ROOT = r"G:\combined-dataset-modifiedNO-BOOK-jalan-jalan-rebalanced"  # un-augmented
OUT_ROOT    = r"G:\combined-dataset-modifiedNO-BOOK-jalan-jalan-rebalanced-70_15_15-prioritize-train"               # NEW output
VAL_FRAC    = 0.10
TEST_FRAC   = 0.10
SEED        = 42
IMAGE_EXTS  = (".jpg", ".jpeg", ".png", ".bmp")
# ============================================

random.seed(SEED)
np.random.seed(SEED)

def yolo_label_path(img_path, labels_root):
    base = os.path.splitext(os.path.basename(img_path))[0] + ".txt"
    return os.path.join(labels_root, base)

def load_split(split_root):
    im_dir = os.path.join(split_root, "images")
    lb_dir = os.path.join(split_root, "labels")
    imgs = [p for ext in IMAGE_EXTS for p in glob.glob(os.path.join(im_dir, f"*{ext}"))]
    pairs = []
    for im in imgs:
        lp = yolo_label_path(im, lb_dir)
        if os.path.exists(lp):
            pairs.append((im, lp))
    return pairs

def read_classes(lb_path):
    ids = set()
    with open(lb_path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if not parts: continue
            try:
                ids.add(int(float(parts[0])))
            except:
                pass
    return sorted(ids)

# 1) Collect originals (train/val/test) without touching source
src_train, src_val, src_test = [os.path.join(SOURCE_ROOT, s) for s in ("train","valid","test")]
pairs = []
for root in (src_train, src_val, src_test):
    if os.path.isdir(root):
        pairs.extend(load_split(root))

images, labels = [], []
for im, lb in pairs:
    cls_list = read_classes(lb)
    if len(cls_list) == 0:
        continue  # skip empty-label images; change if you use background-only
    images.append(im)
    labels.append(cls_list)

if not images:
    raise SystemExit("No labeled images found in source splits.")

all_classes = sorted({c for ls in labels for c in ls})
K = max(all_classes) + 1
print(f"Found {len(images)} images with labels. Detected classes in dataset: {len(all_classes)} (0..{K-1})")

# Build multi-hot matrix
Y = np.zeros((len(images), K), dtype=int)
for i, cls_list in enumerate(labels):
    for c in cls_list:
        Y[i, c] = 1

# Per-class totals and targets (soft)
totals = Y.sum(axis=0)
t_val  = np.round(totals * VAL_FRAC).astype(int)
t_test = np.round(totals * TEST_FRAC).astype(int)

# Enforce minimums per class while prioritizing train
# total=1 -> val=0, test=0; total=2 -> val>=1, test=0; total>=3 -> val>=1, test>=1
for c, n in enumerate(totals):
    if n == 0:  # class not present at all
        continue
    min_val  = 1 if n >= 2 else 0
    min_test = 1 if n >= 3 else 0
    t_val[c]  = max(t_val[c],  min_val)
    t_test[c] = max(t_test[c], min_test)
    # ensure at least 1 left for train
    # if n - t_val - t_test < 1, reduce test first, then val
    while n - t_val[c] - t_test[c] < 1:
        if t_test[c] > min_test:
            t_test[c] -= 1
        elif t_val[c] > min_val:
            t_val[c] -= 1
        else:
            # cannot reduce further; train will be 0 (only possible if n==1 and min_val==min_test==0 -> OK)
            break

# 2) Initial multilabel-stratified split 70 / (15+15), then split temp into val/test
try:
    from iterstrat.ml_stratifiers import MultilabelStratifiedShuffleSplit
except ImportError:
    raise SystemExit("Please install iterative-stratification: pip install iterative-stratification")

msss1 = MultilabelStratifiedShuffleSplit(n_splits=1, test_size=VAL_FRAC + TEST_FRAC, random_state=SEED)
train_idx, temp_idx = next(msss1.split(np.zeros(len(images)), Y))

temp_Y = Y[temp_idx]
test_ratio_in_temp = TEST_FRAC / (VAL_FRAC + TEST_FRAC)
msss2 = MultilabelStratifiedShuffleSplit(n_splits=1, test_size=test_ratio_in_temp, random_state=SEED)
val_sub, test_sub = next(msss2.split(np.zeros(len(temp_idx)), temp_Y))
val_idx  = temp_idx[val_sub]
test_idx = temp_idx[test_sub]

# 3) Helpers
def per_class_counts(idx):
    if len(idx) == 0:
        return np.zeros(K, dtype=int)
    return Y[idx].sum(axis=0)

def ensure_min_train_coverage(train_idx, val_idx, test_idx):
    """Make sure every present class has at least 1 image in TRAIN."""
    tr, va, te = set(train_idx), set(val_idx), set(test_idx)
    tr_counts = per_class_counts(np.array(list(tr)))
    va_counts = per_class_counts(np.array(list(va)))
    te_counts = per_class_counts(np.array(list(te)))

    for c, n in enumerate(totals):
        if n == 0:
            continue
        if tr_counts[c] > 0:
            continue  # already covered

        # we must move one image containing class c from val or test into train
        # prefer the split that has more than its minimum for class c
        min_val  = 1 if n >= 2 else 0
        min_test = 1 if n >= 3 else 0

        # candidates
        cand_val = [i for i in va if Y[i, c] == 1 and va_counts[c] > min_val]
        cand_te  = [i for i in te if Y[i, c] == 1 and te_counts[c] > min_test]

        chosen = None
        if cand_val:
            chosen = min(cand_val, key=lambda i: Y[i].sum())  # fewest classes
            va.remove(chosen); tr.add(chosen)
            # update counts
            va_counts[c] -= 1; tr_counts[c] += 1
        elif cand_te:
            chosen = min(cand_te, key=lambda i: Y[i].sum())
            te.remove(chosen); tr.add(chosen)
            te_counts[c] -= 1; tr_counts[c] += 1
        else:
            # no slack; as a last resort, borrow even if it hits minimums—prefer TEST, then VAL
            cand_te2  = [i for i in te if Y[i, c] == 1]
            cand_val2 = [i for i in va if Y[i, c] == 1]
            if cand_te2:
                chosen = min(cand_te2, key=lambda i: Y[i].sum())
                te.remove(chosen); tr.add(chosen)
                te_counts[c] -= 1; tr_counts[c] += 1
            elif cand_val2:
                chosen = min(cand_val2, key=lambda i: Y[i].sum())
                va.remove(chosen); tr.add(chosen)
                va_counts[c] -= 1; tr_counts[c] += 1
            else:
                print(f"[WARN] Could not place class {c} into TRAIN (total images may be zero).")
    return np.array(sorted(tr)), np.array(sorted(va)), np.array(sorted(te))

def nudge_toward_targets(val_idx, test_idx, train_idx, t_val, t_test, max_moves_per_class=1):
    """
    Very gentle nudge to approach ~15% per-class in VAL/TEST, without violating train>=1
    and while keeping val>=min, test>=min.
    """
    tr, va, te = set(train_idx), set(val_idx), set(test_idx)
    trc = per_class_counts(np.array(list(tr)))
    vac = per_class_counts(np.array(list(va)))
    tec = per_class_counts(np.array(list(te)))

    for c, n in enumerate(totals):
        if n == 0: continue
        min_val  = 1 if n >= 2 else 0
        min_test = 1 if n >= 3 else 0

        # Move from TRAIN to VAL/TEST if they are below their soft targets but train has slack (>1)
        moves = 0
        while moves < max_moves_per_class:
            moved = False
            # prefer filling TEST first, then VAL (or flip if you want)
            if tec[c] < t_test[c] and trc[c] > 1:
                # pick a train image containing c, preferably with few classes
                cand = [i for i in tr if Y[i, c] == 1]
                if cand:
                    i = min(cand, key=lambda x: Y[x].sum())
                    tr.remove(i); te.add(i); trc[c]-=1; tec[c]+=1; moved=True
            if not moved and vac[c] < t_val[c] and trc[c] > 1:
                cand = [i for i in tr if Y[i, c] == 1]
                if cand:
                    i = min(cand, key=lambda x: Y[x].sum())
                    tr.remove(i); va.add(i); trc[c]-=1; vac[c]+=1; moved=True
            if not moved:
                break
            moves += 1
    return np.array(sorted(va)), np.array(sorted(te)), np.array(sorted(tr))

# 4) Enforce train coverage, then gently nudge toward targets
train_idx, val_idx, test_idx = ensure_min_train_coverage(train_idx, val_idx, test_idx)
val_idx, test_idx, train_idx = nudge_toward_targets(val_idx, test_idx, train_idx, t_val, t_test, max_moves_per_class=1)

# Recompute final counts
def pct(a,b): return (a/b*100.0) if b else 0.0
def report(name, idx):
    cnts = per_class_counts(idx)
    present = int((cnts>0).sum())
    missing = [c for c in range(K) if totals[c]>0 and cnts[c]==0]
    print(f"{name}: {len(idx)} images ({pct(len(idx), len(images)):.2f}%) | classes present {present}/{(totals>0).sum()} | missing: {missing[:10]}{'...' if len(missing)>10 else ''}")
    return missing

print("\n=== Final split sizes ===")
missing_train = report("train", train_idx)
missing_val   = report("valid  ", val_idx)
missing_test  = report("test ", test_idx)

# 5) Write out new YOLO folders
def copy_split(indices, split):
    im_out = os.path.join(OUT_ROOT, split, "images")
    lb_out = os.path.join(OUT_ROOT, split, "labels")
    os.makedirs(im_out, exist_ok=True)
    os.makedirs(lb_out, exist_ok=True)
    # Build label lookup once
    if not hasattr(copy_split, "_lbl"):
        d = {}
        for im, lb in pairs:
            d[os.path.basename(im)] = lb
        copy_split._lbl = d
    for i in indices:
        src_img = images[i]
        src_lab = copy_split._lbl[os.path.basename(src_img)]
        shutil.copy2(src_img, os.path.join(im_out, os.path.basename(src_img)))
        shutil.copy2(src_lab, os.path.join(lb_out, os.path.basename(src_lab)))

print("\nCopying files to", OUT_ROOT)
copy_split(train_idx, "train")
copy_split(val_idx,   "valid")
copy_split(test_idx,  "test")
print("Done.")
