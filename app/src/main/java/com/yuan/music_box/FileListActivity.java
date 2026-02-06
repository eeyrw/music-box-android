package com.yuan.music_box;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/* =========================
 * 文件数据模型
 * ========================= */
class FileItem {

    private final String name;
    private final String path;

    public FileItem(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }
}

/* =========================
 * ListView Adapter（支持搜索）
 * ========================= */
class FileItemAdapter extends ArrayAdapter<FileItem> {

    private final int resourceId;
    private final List<FileItem> originalList;
    private final List<FileItem> displayList;

    public FileItemAdapter(Context context, int resourceId, List<FileItem> data) {
        super(context, resourceId, new ArrayList<>(data));
        this.resourceId = resourceId;
        this.originalList = new ArrayList<>(data);
        this.displayList = new ArrayList<>(data);
    }

    @Override
    public int getCount() {
        return displayList.size();
    }

    @Override
    public FileItem getItem(int position) {
        return displayList.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = LayoutInflater.from(getContext())
                .inflate(resourceId, parent, false);

        TextView fileName = view.findViewById(R.id.fileName);
        fileName.setText(getItem(position).getName());

        return view;
    }

    /** 搜索过滤 */
    public void filter(String keyword) {
        displayList.clear();

        if (keyword == null || keyword.isEmpty()) {
            displayList.addAll(originalList);
        } else {
            String lower = keyword.toLowerCase();
            for (FileItem item : originalList) {
                if (item.getName().toLowerCase().contains(lower)) {
                    displayList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }
}

/* =========================
 * Activity
 * ========================= */
public class FileListActivity extends AppCompatActivity {

    private FileItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        ArrayList<FileItem> fileList = new ArrayList<>();

        try {
            String[] midiFiles = getAssets().list("midi-sample");
            if (midiFiles != null) {
                for (String name : midiFiles) {
                    fileList.add(new FileItem(
                            name,
                            "midi-sample/" + name
                    ));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        ListView listView = findViewById(R.id.list_view);
        SearchView searchView = findViewById(R.id.search_view);

        adapter = new FileItemAdapter(
                this,
                R.layout.file_item,
                fileList
        );
        listView.setAdapter(adapter);

        // 搜索监听
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });

        // 点击返回文件路径
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {

                FileItem item = adapter.getItem(position);

                Toast.makeText(
                        FileListActivity.this,
                        item.getName(),
                        Toast.LENGTH_SHORT
                ).show();

                Intent intent = getIntent();
                intent.putExtra("filePath", item.getPath());
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }
}
