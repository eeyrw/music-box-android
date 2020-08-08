package com.example.music_box;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class FileItem {

    private String name;

    private String path;

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


class FileItemAdapter extends ArrayAdapter<FileItem> {

    private int resourceId;

    /**
     * context:当前活动上下文
     * textViewResourceId:ListView子项布局的ID
     * objects：要适配的数据
     */
    public FileItemAdapter(Context context, int textViewResourceId,
                           List<FileItem> objects) {
        super(context, textViewResourceId, objects);
        //拿取到子项布局ID
        resourceId = textViewResourceId;
    }

    /**
     * LIstView中每一个子项被滚动到屏幕的时候调用
     * position：滚到屏幕中的子项位置，可以通过这个位置拿到子项实例
     * convertView：之前加载好的布局进行缓存
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        FileItem fileItem = getItem(position);  //获取当前项的Fruit实例
        //为子项动态加载布局
        View view = LayoutInflater.from(getContext()).inflate(resourceId, null);
        TextView fileName = (TextView) view.findViewById(R.id.fileName);
        fileName.setText(fileItem.getName());
        return view;
    }

}

public class FileListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);
        final ArrayList<FileItem> fileList = new ArrayList<FileItem>();


        try {
            //获取/assets/目录下所有文件
            String[] midiFileList = getAssets().list("midi-sample");
            for (String filePath : midiFileList) {
                fileList.add(new FileItem(filePath, String.format("midi-sample/%s", filePath)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileItemAdapter adapter = new FileItemAdapter(FileListActivity.this, R.layout.file_item, fileList);
        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                FileItem fileItem = fileList.get(position);
                Toast.makeText(FileListActivity.this, fileItem.getName(), Toast.LENGTH_SHORT).show();

                Intent intent = getIntent();
                //这里使用bundle绷带来传输数据
                Bundle bundle = new Bundle();
                //传输的内容仍然是键值对的形式
                bundle.putString("filePath", fileItem.getPath());
                intent.putExtras(bundle);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }


}