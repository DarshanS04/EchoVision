package com.visionassist.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.visionassist.R;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;

/**
 * Main assistant fragment showing status and last response.
 * Embedded in MainActivity.
 */
public class AssistantFragment extends Fragment {

    private static final String TAG = "AssistantFragment";

    private TextView statusLabel;
    private TextView responseLabel;
    private AppRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_assistant, container, false);

        statusLabel = view.findViewById(R.id.assistant_status);
        responseLabel = view.findViewById(R.id.assistant_response);
        repository = AppRepository.getInstance(requireContext());

        updateUI();
        AppLogger.d(TAG, "AssistantFragment created");
        return view;
    }

    public void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    public void updateResponse(String response) {
        if (responseLabel != null) {
            responseLabel.setText(response);
        }
        if (repository != null) {
            repository.setLastSpokenResponse(response);
        }
    }

    private void updateUI() {
        if (repository != null) {
            String lastResponse = repository.getLastSpokenResponse();
            if (!lastResponse.isEmpty() && responseLabel != null) {
                responseLabel.setText(lastResponse);
            }
        }
    }
}
