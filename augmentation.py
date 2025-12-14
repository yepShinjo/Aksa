# augmentation.py
# YOLO-style dataset augmentation with a gentle, readability-safe pipeline
# Folder layout expected:
#   dataset_root/
#       train/images, train/labels
#       valid/images, valid/labels
#       test/images,  test/labels
#
# Usage (Windows example):
#   python augmentation.py ^
#     --data-root "G:\combined-dataset-modified-NO-BOOK-rebalanced" ^
#     --out-root  "G:\combined-dataset-modified-NO-BOOK-rebalanced-augmented" ^
#     --aug-per-image 4 --img-size 640 --copy-valid --copy-test

#   python augmentation.py --data-root "G:\combined-dataset-modifiedNO-BOOK-jalan-jalan-rebalanced-70_15_15-stratified" --out-root  "G:\combined-dataset-modifiedNO-BOOK-jalan-jalan-rebalanced-70_15_15-stratified-augmented" --aug-per-image 4 --img-size 640 --copy-valid --copy-test

#
# Deps:
#   pip install albumentations opencv-python tqdm numpy packaging

import os
import argparse
from pathlib import Path
import shutil
import random
import json

import cv2
import numpy as np
from tqdm import tqdm
import albumentations as A
from packaging.version import Version

# ------------------------------ Config defaults ------------------------------
DEFAULT_AUG_PER_IMAGE = 4
RANDOM_SEED = 42
IMG_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

# If True, images that have no .txt will be skipped (not augmented)
SKIP_IF_NO_LABEL = False

# Albumentations v2 detection
ALB2 = Version(A.__version__) >= Version("2.0.0")


# ------------------------------ Helpers ------------------------------
def list_images(images_dir: Path):
    return sorted([p for p in images_dir.glob("*") if p.suffix.lower() in IMG_EXTS])


def read_yolo_labels(label_path: Path):
    """Return (bboxes, class_labels). bboxes are [x, y, w, h] normalized floats."""
    if not label_path.exists():
        return [], []
    bboxes, classes = [], []
    with open(label_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) != 5:
                # Skip malformed lines
                continue
            cls_id = int(float(parts[0]))
            x, y, w, h = map(float, parts[1:])
            # Clamp to [0,1]
            x = min(max(x, 0.0), 1.0)
            y = min(max(y, 0.0), 1.0)
            w = min(max(w, 0.0), 1.0)
            h = min(max(h, 0.0), 1.0)
            bboxes.append([x, y, w, h])
            classes.append(cls_id)
    return bboxes, classes


def write_yolo_labels(label_path: Path, bboxes, classes):
    label_path.parent.mkdir(parents=True, exist_ok=True)
    with open(label_path, "w", encoding="utf-8") as f:
        for (x, y, w, h), cls_id in zip(bboxes, classes):
            f.write(f"{int(cls_id)} {x:.6f} {y:.6f} {w:.6f} {h:.6f}\n")


def ensure_dirs(out_root: Path):
    for split in ("train", "valid", "test"):
        (out_root / split / "images").mkdir(parents=True, exist_ok=True)
        (out_root / split / "labels").mkdir(parents=True, exist_ok=True)


def copy_split(src_root: Path, out_root: Path, split: str):
    """Copy a split (valid/test) as-is (no augmentation)."""
    src_img = src_root / split / "images"
    src_lbl = src_root / split / "labels"
    dst_img = out_root / split / "images"
    dst_lbl = out_root / split / "labels"
    dst_img.mkdir(parents=True, exist_ok=True)
    dst_lbl.mkdir(parents=True, exist_ok=True)

    count_imgs = 0
    count_lbls = 0
    for p in list_images(src_img):
        shutil.copy2(p, dst_img / p.name)
        count_imgs += 1
        lp = src_lbl / (p.stem + ".txt")
        if lp.exists():
            shutil.copy2(lp, dst_lbl / lp.name)
            count_lbls += 1
    return count_imgs, count_lbls


# ---- Version-safe small helpers ----
def GAUSS_NOISE(p=0.25, var=(1.0, 10.0)):
    """
    Mild Gaussian noise. Albumentations v2 uses GaussianNoise; v1 uses GaussNoise.
    var is variance range, tuned low to not obstruct glyphs.
    """
    if hasattr(A, "GaussianNoise"):  # v2
        return A.GaussianNoise(mean=0, var_limit=var, per_channel=False, p=p)
    else:  # v1
        return A.GaussNoise(var_limit=var, p=p)


# ------------------------------ Gentle Pipeline ------------------------------
def build_pipelines(img_size=640):
    """
    Minimal, gentle pipeline for Aksara Bali OCR/classification:
    - Rotation only (−8°..+8°)
    - Saturation ±17%
    - Very light blur (~1px, 3x3 kernel)
    - Mild Gaussian noise
    """
    bbox_params = A.BboxParams(
        format="yolo",
        label_fields=["class_labels"],
        min_visibility=0.2,
        check_each_transform=False,
    )

    minimal_gentle = A.Compose(
        [
            # Geometry: rotation only (no flips/perspective)
            A.Affine(
                rotate=(-8, 8),
                scale=1.0,
                translate_percent=(0.0, 0.0),
                shear={"x": (0, 0), "y": (0, 0)},
                fit_output=False,
                p=0.9,
            ),

            # Color: saturation within ±17% (brightness/contrast/hue untouched)
            A.ColorJitter(
                brightness=(1.0, 1.0),
                contrast=(1.0, 1.0),
                saturation=(0.83, 1.17),
                hue=0,
                p=0.8,
            ),

            # Optics: very light blur (~1px)
            A.GaussianBlur(blur_limit=(3, 3), p=0.35),

            # Sensor noise: mild Gaussian noise
            GAUSS_NOISE(p=0.25, var=(1.0, 10.0)),

            # Final size
            A.Resize(img_size, img_size, p=1.0),
        ],
        bbox_params=bbox_params,
    )

    # Single clean pipeline
    return [("gentle", minimal_gentle)]


# ------------------------------ Core Augmentation ------------------------------
def augment_train(src_root: Path, out_root: Path, aug_per_image: int, img_size: int):
    src_img_dir = src_root / "train" / "images"
    src_lbl_dir = src_root / "train" / "labels"
    dst_img_dir = out_root / "train" / "images"
    dst_lbl_dir = out_root / "train" / "labels"

    pipelines = build_pipelines(img_size=img_size)
    names = [n for n, _ in pipelines]

    originals = 0
    created = 0
    dropped_boxes = 0
    skipped_no_label = 0

    # Copy originals first
    for p in list_images(src_img_dir):
        dst = dst_img_dir / p.name
        shutil.copy2(p, dst)
        originals += 1
        lbl_src = src_lbl_dir / (p.stem + ".txt")
        if lbl_src.exists():
            shutil.copy2(lbl_src, dst_lbl_dir / lbl_src.name)

    # Augment
    for img_path in tqdm(list_images(src_img_dir), desc="Augmenting train"):
        label_path = src_lbl_dir / (img_path.stem + ".txt")
        bboxes, class_labels = read_yolo_labels(label_path)

        if not bboxes:
            if SKIP_IF_NO_LABEL:
                skipped_no_label += 1
                continue

        image = cv2.imread(str(img_path))
        if image is None:
            continue
        image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)

        # Use the single gentle pipeline aug_per_image times
        for k in range(aug_per_image):
            pipe_name, pipe = pipelines[0]
            transformed = pipe(image=image, bboxes=bboxes, class_labels=class_labels)
            aug_img = transformed["image"]
            aug_boxes = transformed["bboxes"]
            aug_classes = transformed["class_labels"]

            vanished = len(aug_boxes) == 0 and len(bboxes) > 0
            if vanished:
                dropped_boxes += 1

            out_name = f"{img_path.stem}_aug{k}-{pipe_name}"
            out_img_path = dst_img_dir / f"{out_name}.jpg"
            out_lbl_path = dst_lbl_dir / f"{out_name}.txt"

            cv2.imwrite(str(out_img_path), cv2.cvtColor(aug_img, cv2.COLOR_RGB2BGR))
            if len(aug_boxes) > 0:
                write_yolo_labels(out_lbl_path, aug_boxes, aug_classes)
            created += 1

    return {
        "originals": originals,
        "augmented": created,
        "dropped_boxes": dropped_boxes,
        "skipped_no_label": skipped_no_label,
        "pipelines": names,
    }


def summarize(out_root: Path):
    def count_split(split):
        img_dir = out_root / split / "images"
        lbl_dir = out_root / split / "labels"
        imgs = sum(1 for _ in img_dir.glob("*") if _.suffix.lower() in IMG_EXTS)
        lbls = sum(1 for _ in lbl_dir.glob("*.txt"))
        return imgs, lbls

    stats = {}
    for sp in ["train", "valid", "test"]:
        stats[sp] = count_split(sp)
    return stats


# ------------------------------ CLI ------------------------------
def main():
    parser = argparse.ArgumentParser("YOLO dataset augmentation (gentle)")
    parser.add_argument("--data-root", type=str, required=True, help="Path to dataset root (contains train/ valid/ test/)")
    parser.add_argument("--out-root", type=str, required=True, help="Path to output root")
    parser.add_argument("--aug-per-image", type=int, default=DEFAULT_AUG_PER_IMAGE, help="How many aug variants per train image")
    parser.add_argument("--img-size", type=int, default=640, help="Final resize (square)")
    parser.add_argument("--copy-valid", action="store_true", help="Copy valid split to output")
    parser.add_argument("--copy-test", action="store_true", help="Copy test split to output")
    args = parser.parse_args()

    random.seed(RANDOM_SEED)
    np.random.seed(RANDOM_SEED)

    src_root = Path(args.data_root)
    out_root = Path(args.out_root)

    ensure_dirs(out_root)

    # Copy valid/test as-is if requested
    copied = {}
    for sp, flag in [("valid", args.copy_valid), ("test", args.copy_test)]:
        if flag:
            imgs, lbls = copy_split(src_root, out_root, sp)
            copied[sp] = {"images": imgs, "labels": lbls}

    # Augment train
    aug_stats = augment_train(src_root, out_root, args.aug_per_image, args.img_size)

    # Summary
    full_stats = summarize(out_root)
    print("\n===== SUMMARY =====")
    print(f"train: originals={aug_stats['originals']}, augmented={aug_stats['augmented']}, total_images={full_stats['train'][0]}, total_labels={full_stats['train'][1]}")
    print(f"valid: total_images={full_stats['valid'][0]}, total_labels={full_stats['valid'][1]} (copied: {copied.get('valid', {})})")
    print(f"test : total_images={full_stats['test'][0]},  total_labels={full_stats['test'][1]}  (copied: {copied.get('test', {})})")
    all_total = full_stats['train'][0] + full_stats['valid'][0] + full_stats['test'][0]
    print(f"ALL  : total_images={all_total}")
    print(f"dropped_augmented_with_no_boxes={aug_stats['dropped_boxes']}  skipped_no_label={aug_stats['skipped_no_label']}")
    print("pipelines:", ", ".join(aug_stats["pipelines"]))
    print("===================")

    # Manifest
    manifest = {
        "pipelines": aug_stats["pipelines"],
        "train": {"originals": aug_stats["originals"], "augmented": aug_stats["augmented"]},
        "dropped_augmented_with_no_boxes": aug_stats["dropped_boxes"],
        "skipped_no_label": aug_stats["skipped_no_label"],
        "final_counts": {
            "train_images": full_stats["train"][0],
            "train_labels": full_stats["train"][1],
            "valid_images": full_stats["valid"][0],
            "valid_labels": full_stats["valid"][1],
            "test_images": full_stats["test"][0],
            "test_labels": full_stats["test"][1],
        },
    }
    (out_root / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
