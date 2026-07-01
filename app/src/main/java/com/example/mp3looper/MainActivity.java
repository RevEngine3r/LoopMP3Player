package com.example.mp3looper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_MP3 = 1001;

    private TextView selectedPathView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectedPathView = (TextView) findViewById(R.id.selected_path);
        statusView = (TextView) findViewById(R.id.status_text);
        Button browseButton = (Button) findViewById(R.id.browse_button);
        Button enterPathButton = (Button) findViewById(R.id.enter_path_button);

        browseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, FileBrowserActivity.class);
                startActivityForResult(intent, REQUEST_PICK_MP3);
            }
        });

        enterPathButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showManualPathDialog();
            }
        });

        refreshSelectedPath();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSelectedPath();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_MP3 || resultCode != RESULT_OK || data == null) {
            return;
        }

        String selectedPath = data.getStringExtra(FileBrowserActivity.EXTRA_SELECTED_PATH);
        if (TextUtils.isEmpty(selectedPath)) {
            Toast.makeText(this, R.string.error_no_file_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(selectedPath);
        if (!file.isFile()) {
            Toast.makeText(this, R.string.error_file_missing, Toast.LENGTH_SHORT).show();
            return;
        }

        savePathAndStart(file.getAbsolutePath());
    }

    private void refreshSelectedPath() {
        String path = Prefs.loadMp3Path(this);
        if (TextUtils.isEmpty(path)) {
            selectedPathView.setText(R.string.no_file_selected);
            statusView.setText(R.string.status_idle);
            return;
        }

        selectedPathView.setText(path);
        statusView.setText(R.string.status_ready);
    }

    private void showManualPathDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("/sdcard/Music/file.mp3");

        String currentPath = Prefs.loadMp3Path(this);
        if (!TextUtils.isEmpty(currentPath)) {
            input.setText(currentPath);
            input.setSelection(currentPath.length());
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.enter_path_button)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        String enteredPath = input.getText().toString().trim();
                        if (TextUtils.isEmpty(enteredPath)) {
                            Toast.makeText(MainActivity.this, R.string.error_no_file_selected, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        File file = new File(enteredPath);
                        if (!file.isFile()) {
                            Toast.makeText(MainActivity.this, R.string.error_file_missing, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        savePathAndStart(file.getAbsolutePath());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void savePathAndStart(String absolutePath) {
        Prefs.saveMp3Path(this, absolutePath);
        PlaybackService.start(this);
        refreshSelectedPath();
        Toast.makeText(this, R.string.saved_and_started, Toast.LENGTH_SHORT).show();
    }
}
