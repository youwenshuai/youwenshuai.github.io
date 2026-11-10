package com.youwenshuai.blogwriter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EditorActivity extends Activity {

    SharedPreferences prefs;
    String name, sha, token, owner, repo, branch, dir;
    EditText fileField, bodyField;
    TextView status;
    Button saveBtn;
    boolean isNew;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        name = getIntent().getStringExtra("name");
        sha = getIntent().getStringExtra("sha");
        isNew = (sha == null);
        loadCfg();

        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(isNew ? "写新文章" : "编辑: " + name);
        title.setTextSize(20);
        root.addView(title);

        status = new TextView(this);
        status.setPadding(0, (int) (6 * dp), 0, 0);
        root.addView(status);

        if (isNew) {
            fileField = new EditText(this);
            fileField.setSingleLine(true);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            fileField.setText(today + "-untitled.md");
            root.addView(fileField);
        }

        bodyField = new EditText(this);
        bodyField.setHint("用 Markdown 写你的文章…");
        bodyField.setGravity(Gravity.TOP);
        bodyField.setTextSize(15);
        bodyField.setTypeface(android.graphics.Typeface.MONOSPACE);
        bodyField.setMinLines(6);
        root.addView(bodyField, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        saveBtn = new Button(this);
        saveBtn.setText("保存并上传");
        saveBtn.setOnClickListener(v -> save());
        root.addView(saveBtn);

        setContentView(root);

        // 编辑已有文章时先拉取内容
        if (!isNew) fetchContent();

        bodyField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c2, int d) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c2) {}
            public void afterTextChanged(Editable s) {
                saveBtn.setText("保存并上传（草稿仅保存在手机内存中）");
            }
        });
    }

    void loadCfg() {
        token = prefs.getString("token", "");
        owner = prefs.getString("owner", "youwenshuai");
        repo = prefs.getString("repo", "youwenshuai.github.io");
        branch = prefs.getString("branch", "hexo-source");
        dir = prefs.getString("dir", "source/_posts");
    }

    void fetchContent() {
        status.setText("正在拉取原文…");
        new Thread(() -> {
            try {
                String url = "https://api.github.com/repos/" + owner + "/" + repo
                        + "/contents/" + GitHubApi.encPath(dir + "/" + name)
                        + "?ref=" + GitHubApi.enc(branch);
                JSONObject obj = new JSONObject(GitHubApi.request("GET", url, token, null));
                String b64 = obj.getString("content").replaceAll("\n", "");
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                String text = new String(data, StandardCharsets.UTF_8);
                sha = obj.getString("sha");
                runOnUiThread(() -> {
                    bodyField.setText(text);
                    status.setText("已加载，可以开始修改");
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("拉取失败: " + e.getMessage()));
            }
        }).start();
    }

    void save() {
        String fileName = isNew ? fileField.getText().toString().trim() : name;
        if (fileName.isEmpty()) {
            Toast.makeText(this, "请填写文件名", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!fileName.endsWith(".md")) fileName = fileName + ".md";
        String content = bodyField.getText().toString();
        if (content.trim().isEmpty()) {
            Toast.makeText(this, "正文是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isNew && !content.trim().startsWith("---")) {
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date());
            String title = fileName.substring(0, fileName.length() - 3);
            content = "---\ntitle: " + title + "\ndate: " + date + "\ntags:\n---\n\n" + content;
        }

        final String fname = fileName;
        final String bodyText = content;
        saveBtn.setEnabled(false);
        status.setText("正在上传 " + fname + " …");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("message", (isNew ? "新文章: " : "更新文章: ") + fname + "（手机端）");
                body.put("content", Base64.encodeToString(
                        bodyText.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                body.put("branch", branch);
                if (!isNew) body.put("sha", sha);
                String url = "https://api.github.com/repos/" + owner + "/" + repo
                        + "/contents/" + GitHubApi.encPath(dir + "/" + fname);
                GitHubApi.request("PUT", url, token, body);
                runOnUiThread(() -> {
                    saveBtn.setEnabled(true);
                    Toast.makeText(this, "已上传！云端正在自动发布", Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    saveBtn.setEnabled(true);
                    status.setText("上传失败: " + e.getMessage());
                });
            }
        }).start();
    }
}
