from tflite_support.metadata_writers import object_detector
from tflite_support.metadata_writers import writer_utils

MODEL_PATH = "D:/SKRIPSI/tflite_file/best_77_F1_Score_float32.tflite"      # your model file
LABEL_FILE = "D:/SKRIPSI/tflite_file/72labels.txt"               # your label file
EXPORT_PATH = "D:/SKRIPSI/tflite_file_injected/injected_best_77_F1_Score.tflite"

# Use [127.5] for both if your input is normalized as (x-127.5)/127.5, otherwise adjust accordingly.
writer = object_detector.MetadataWriter.create_for_inference(
    model_buffer=open(MODEL_PATH, 'rb').read(),
    input_norm_mean=[127.5],
    input_norm_std=[127.5],
    label_file_paths=[LABEL_FILE]
)
writer_utils.save_file(writer.populate(), EXPORT_PATH)
print(f"Model with metadata saved to: {EXPORT_PATH}")
