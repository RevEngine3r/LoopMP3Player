package com.example.mp3looper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

public class FileBrowserActivity extends Activity {
    public static final String EXTRA_SELECTED_PATH = "selected_path";

    private TextView currentPathView;
    private ArrayAdapter<String> adapter;
    private final List<File> currentEntries = new ArrayList<File>();
    private final List<File> rootEntries = new ArrayList<File>();
    private File currentDirectory;
    private boolean showingRoots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);

        currentPathView = (TextView) findViewById(R.id.current_path);
        ListView listView = (ListView) findViewById(R.id.file_list);
        Button upButton = (Button) findViewById(R.id.up_button);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        listView.setAdapter(adapter);

        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (showingRoots) {
                    return;
                }
                if (currentDirectory != null && currentDirectory.getParentFile() != null) {
                    loadDirectory(currentDirectory.getParentFile());
                } else {
                    showRoots();
                }
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long itemId) {
                if (position >= currentEntries.size()) {
                    return;
                }
                File selected = currentEntries.get(position);
                if (showingRoots) {
                    loadDirectory(selected);
                    return;
                }
                if (selected.isDirectory()) {
                    loadDirectory(selected);
                    return;
                }

                Intent data = new Intent();
                data.putExtra(EXTRA_SELECTED_PATH, selected.getAbsolutePath());
                setResult(RESULT_OK, data);
                finish();
            }
        });

        buildRootEntries();
        if (rootEntries.isEmpty()) {
            Toast.makeText(this, R.string.error_storage_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        showRoots();
    }

    private void loadDirectory(File directory) {
        showingRoots = false;
        File[] listedFiles = directory.listFiles();

        if (listedFiles == null) {
            Toast.makeText(this, R.string.error_open_folder, Toast.LENGTH_SHORT).show();
            return;
        }

        List<File> files = Arrays.asList(listedFiles);
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() && !right.isDirectory()) {
                    return -1;
                }
                if (!left.isDirectory() && right.isDirectory()) {
                    return 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });

        currentDirectory = directory;
        currentEntries.clear();
        currentEntries.addAll(files);

        List<String> labels = new ArrayList<String>();
        for (File file : currentEntries) {
            if (file.isDirectory()) {
                labels.add("[DIR] " + file.getName());
            } else if (isMp3File(file)) {
                labels.add("[MP3] " + file.getName());
            } else {
                labels.add("[FILE] " + file.getName());
            }
        }

        if (labels.isEmpty()) {
            labels.add(getString(R.string.empty_folder));
        }

        currentPathView.setText(TextUtils.isEmpty(directory.getAbsolutePath()) ? "/" : directory.getAbsolutePath());
        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();
    }

    private void showRoots() {
        showingRoots = true;
        currentDirectory = null;
        currentEntries.clear();
        currentEntries.addAll(rootEntries);

        List<String> labels = new ArrayList<String>();
        for (File file : rootEntries) {
            labels.add("[STORAGE] " + file.getAbsolutePath());
        }

        currentPathView.setText(getString(R.string.storage_roots_label));
        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();
    }

    private void buildRootEntries() {
        Set<String> seenPaths = new LinkedHashSet<String>();
        addRootIfValid(seenPaths, Environment.getExternalStorageDirectory());
        addRootIfValid(seenPaths, new File("/storage"));
        addRootIfValid(seenPaths, new File("/mnt"));
        addRootIfValid(seenPaths, new File("/sdcard"));
        addRootIfValid(seenPaths, new File("/mnt/sdcard"));
        addRootIfValid(seenPaths, new File("/storage/sdcard0"));
        addRootIfValid(seenPaths, new File("/storage/sdcard1"));
        addRootIfValid(seenPaths, new File("/storage/ext_sd"));
        addRootIfValid(seenPaths, new File("/mnt/ext_sd"));
    }

    private void addRootIfValid(Set<String> seenPaths, File file) {
        if (file == null || !file.exists() || !file.isDirectory() || !file.canRead()) {
            return;
        }
        String path = file.getAbsolutePath();
        if (seenPaths.contains(path)) {
            return;
        }
        seenPaths.add(path);
        rootEntries.add(file);
    }

    private boolean isMp3File(File file) {
        if (file.isDirectory()) {
            return false;
        }

        String normalizedName = file.getName().trim().toLowerCase(Locale.US);
        while (normalizedName.endsWith(".") || normalizedName.endsWith(" ")) {
            normalizedName = normalizedName.substring(0, normalizedName.length() - 1).trim();
        }
        return normalizedName.endsWith(".mp3");
    }
}
