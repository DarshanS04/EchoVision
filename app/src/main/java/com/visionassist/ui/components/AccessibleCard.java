package com.visionassist.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.visionassist.R;

/**
 * High-contrast accessible card with large text.
 * Used for displaying information items in a screen-reader-friendly way.
 */
public class AccessibleCard extends LinearLayout {

    private TextView titleText;
    private TextView bodyText;

    public AccessibleCard(Context context) {
        super(context);
        init(context);
    }

    public AccessibleCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AccessibleCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setPadding(32, 24, 32, 24);
        // Background handled by XML theme
    }

    public void setTitle(String title) {
        if (titleText != null) {
            titleText.setText(title);
            updateContentDescription();
        }
    }

    public void setBody(String body) {
        if (bodyText != null) {
            bodyText.setText(body);
            updateContentDescription();
        }
    }

    private void updateContentDescription() {
        String title = titleText != null ? titleText.getText().toString() : "";
        String body = bodyText != null ? bodyText.getText().toString() : "";
        setContentDescription(title + ". " + body);
    }
}
