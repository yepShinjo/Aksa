# verify_split_labels.py
import os, glob, csv
from collections import Counter
import yaml  # NEW: for reading data.yaml

# ========= CONFIG =========
DATASET_ROOT = r"G:\combined-dataset-modifiedNO-BOOK-jalan-jalan-rebalanced-70_15_15-stratified"
SPLITS = ["train", "valid", "test"]   # your folder names
NUM_CLASSES = 72                      # 0..71
OUTPUT_CSV = os.path.join(DATASET_ROOT, "verify_split_summary.csv")
DATA_YAML = r"G:\combined-dataset-modifiedNO-BOOK-jalan-jalan-rebalanced-70_15_15-stratified\data.yaml"
# =========================


# ---- Load class names from data.yaml ----
def load_class_names(data_yaml_path, num_classes):
    with open(data_yaml_path, "r", encoding="utf-8") as f:
        data_cfg = yaml.safe_load(f)

    names = data_cfg.get("names", {})
    # names can be: list ["ha", "na", ...] or dict {0: "ha", 1: "na", ...}
    if isinstance(names, dict):
        class_names = [None] * num_classes
        for k, v in names.items():
            try:
                idx = int(k)
            except Exception:
                continue
            if 0 <= idx < num_classes:
                class_names[idx] = str(v)
        for i in range(num_classes):
            if class_names[i] is None:
                class_names[i] = f"class_{i}"
    elif isinstance(names, list):
        class_names = [str(n) for n in names]
        if len(class_names) < num_classes:
            class_names += [f"class_{i}" for i in range(len(class_names), num_classes)]
        class_names = class_names[:num_classes]
    else:
        class_names = [f"class_{i}" for i in range(num_classes)]

    return class_names


CLASS_NAMES = load_class_names(DATA_YAML, NUM_CLASSES)


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
def get(d, k):
    return int(d.get(k, 0))


print("\n================ VERIFY SPLITS ================")
print("Per-class counts (images that contain the class) and (instances):\n")

# Header
print(
    f"{'ID':>3}  {'ClassName':<15}  "
    + "  ".join(f"{s[:5].upper():>5}" for s in SPLITS)
    + "   "
    + "  ".join(f"{(s[:5] + '-inst').upper():>9}" for s in SPLITS)
    + "   "
    + f"{'TOTAL':>5}   {'TOTAL_INST':>10}"
)
print("-" * 110)

for c in range(NUM_CLASSES):
    img_counts = [get(split_img_counts[s], c) for s in SPLITS]
    inst_counts = [get(split_inst_counts[s], c) for s in SPLITS]
    total_img = sum(img_counts)
    total_inst = sum(inst_counts)
    cls_name = CLASS_NAMES[c] if c < len(CLASS_NAMES) else f"class_{c}"

    print(
        f"{c:3d}  {cls_name:<15}  "
        + "  ".join(f"{n:5d}" for n in img_counts)
        + "   "
        + "  ".join(f"{n:9d}" for n in inst_counts)
        + f"   {total_img:5d}   {total_inst:10d}"
    )


# ---- Write CSV proof ----
with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow([
        "class_id", "class_name",
        *[f"{s}_images" for s in SPLITS],
        *[f"{s}_instances" for s in SPLITS],
        "total_images", "total_instances",
        "present_in_train", "present_in_valid", "present_in_test",
    ])
    for c in range(NUM_CLASSES):
        img_counts = [get(split_img_counts[s], c) for s in SPLITS]
        inst_counts = [get(split_inst_counts[s], c) for s in SPLITS]
        cls_name = CLASS_NAMES[c] if c < len(CLASS_NAMES) else f"class_{c}"
        w.writerow([
            c,
            cls_name,
            *img_counts,
            *inst_counts,
            sum(img_counts),
            sum(inst_counts),
            *(1 if get(split_img_counts[s], c) > 0 else 0 for s in SPLITS),
        ])

print(f"\nSaved CSV summary to: {OUTPUT_CSV}")
print("Done.\n")
