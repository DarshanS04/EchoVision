package com.visionassist.data.models;

import android.graphics.RectF;

/**
 * Represents a single object detection result from TFLite.
 */
public class DetectionResult {

    private final String label;
    private final float confidence;
    private final RectF boundingBox;
    private String voiceDescription;

    public DetectionResult(String label, float confidence, RectF boundingBox) {
        this.label = label;
        this.confidence = confidence;
        this.boundingBox = boundingBox;
        this.voiceDescription = buildVoiceDescription();
    }

    private String buildVoiceDescription() {
        // Determine position in frame
        String position = getPositionDescription();
        int percent = (int) (confidence * 100);
        return String.format("I can see a %s %s with %d%% confidence.", label, position, percent);
    }

    private String getPositionDescription() {
        if (boundingBox == null) return "in the frame";
        float centerX = boundingBox.centerX();
        // Assuming normalized 0-1 coordinates
        if (centerX < 0.33f) return "on the left";
        if (centerX > 0.66f) return "on the right";
        return "in the centre";
    }

    public String getLabel() { return label; }
    public float getConfidence() { return confidence; }
    public RectF getBoundingBox() { return boundingBox; }
    public String getVoiceDescription() { return voiceDescription; }

    public void setVoiceDescription(String voiceDescription) {
        this.voiceDescription = voiceDescription;
    }

    @Override
    public String toString() {
        return "DetectionResult{label='" + label + "', confidence=" + confidence + "}";
    }
}
