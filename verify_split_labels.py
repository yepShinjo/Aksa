# verify_split_labels.py
import os, glob, csv
from collections import Counter

# ========= CONFIG =========
DATASET_ROOT = r"G:\combined-dataset-modifiedNO-BOOK-jalan-jalan-rebalanced-70_15_15-stratified-augmented"
SPLITS = ["train", "valid", "test"]   # your folder names
NUM_CLASSES = 72                      # 0..71
OUTPUT_CSV = os.path.join(DATASET_ROOT, "verify_split_summary.csv")
# =========================

def read_label_file(path):
    """
    Returns:
      classes_in_image: set of class ids that appear at least once in this image
      per_class_instances: Counter of instances per class in this image
    """
    classes_in_image = set()
    per_class_instances = Counter()
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if not parts:
                continue
            try:
                cls = int(float(parts[0]))
            except ValueError:
                continue
            classes_in_image.add(cls)
            per_class_instances[cls] += 1
    return classes_in_image, per_class_instances

def scan_split(split_name):
    """Scan one split and return (image_counts, instance_counts, total_label_files)."""
    label_dir = os.path.join(DATASET_ROOT, split_name, "labels")
    if not os.path.isdir(label_dir):
        print(f"[WARN] Missing labels dir: {label_dir}")
        return Counter(), Counter(), 0

    image_counts = Counter()    # number of images that contain the class
    instance_counts = Counter() # total number of boxes of the class
    total_files = 0

    for lbl in glob.glob(os.path.join(label_dir, "*.txt")):
        total_files += 1
        classes_in_image, per_img_instances = read_label_file(lbl)
        for c in classes_in_image:
            image_counts[c] += 1
        for c, n in per_img_instances.items():
            instance_counts[c] += n

    return image_counts, instance_counts, total_files

# ---- Scan all splits ----
split_img_counts = {}
split_inst_counts = {}
split_totals = {}
for s in SPLITS:
    img_c, inst_c, tot = scan_split(s)
    split_img_counts[s] = img_c
    split_inst_counts[s] = inst_c
    split_totals[s] = tot

# ---- Print nice summary to console ----
def get(d, k): return int(d.get(k, 0))

print("\n================ VERIFY SPLITS ================")
print("Per-class counts (images that contain the class) and (instances):\n")
header = (
    f"{'Class':>5}  "
    + "  ".join(f"{s[:5].upper():>5}" for s in SPLITS)
    + "   "
    + "  ".join(f"{(s[:5]+'-inst').upper():>9}" for s in SPLITS)
    + "   {'TOTAL':>5}   {'TOTAL_INST':>10}"
)
print(f"{'Class':>5}  " + "  ".join(f"{s[:5].upper():>5}" for s in SPLITS) +
      "   " + "  ".join(f"{(s[:5]+'-inst').upper():>9}" for s in SPLITS) +
      "   " + f"{'TOTAL':>5}   {'TOTAL_INST':>10}")
print("-" * 92)

for c in range(NUM_CLASSES):
    img_counts = [ get(split_img_counts[s], c)   for s in SPLITS ]
    inst_counts= [ get(split_inst_counts[s], c)  for s in SPLITS ]
    total_img  = sum(img_counts)
    total_inst = sum(inst_counts)
    print(
        f"{c:5d}  "
        + "  ".join(f"{n:5d}" for n in img_counts)
        + "   "
        + "  ".join(f"{n:9d}" for n in inst_counts)
        + f"   {total_img:5d}   {total_inst:10d}"
    )

# ---- Write CSV proof ----
with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow([
        "class_id",
        *[f"{s}_images" for s in SPLITS],
        *[f"{s}_instances" for s in SPLITS],
        "total_images", "total_instances",
        "present_in_train", "present_in_valid", "present_in_test"
    ])
    for c in range(NUM_CLASSES):
        img_counts = [ get(split_img_counts[s], c)  for s in SPLITS ]
        inst_counts= [ get(split_inst_counts[s], c) for s in SPLITS ]
        w.writerow([
            c,
            *img_counts,
            *inst_counts,
            sum(img_counts),
            sum(inst_counts),
            *(1 if get(split_img_counts[s], c) > 0 else 0 for s in SPLITS)
        ])

print(f"\nSaved CSV summary to: {OUTPUT_CSV}")
print("Done.\n")
