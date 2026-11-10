package com.youwenshuai.blogwriter;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EditorActivity extends Activity {

    static final int REQ_GALLERY = 101;
    static final int REQ_CAMERA = 102;

    SharedPreferences prefs;
    String name, sha, token, owner, repo, branch, dir;
    EditText fileField, bodyField;
    TextView status;
    Button saveBtn, toggleBtn, imgBtn;
    WebView webView;
    boolean isNew;
    boolean previewing = false;
    Uri pendingCameraUri;
    int pendingInsertPos = -1;

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

        // 预览切换行
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        status = new TextView(this);
        status.setPadding(0, (int) (6 * dp), 0, 0);
        status.setTextSize(13);
        bar.addView(status, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        toggleBtn = new Button(this);
        toggleBtn.setText("预览");
        toggleBtn.setOnClickListener(v -> togglePreview());
        bar.addView(toggleBtn);
        root.addView(bar);

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
        bodyField.setTextSize(16);
        bodyField.setTypeface(Typeface.MONOSPACE);
        bodyField.setLineSpacing((int) (5 * dp), 1.15f);
        bodyField.setMinLines(6);
        root.addView(bodyField, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDefaultTextEncodingName("utf-8");
        webView.setVisibility(View.GONE);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 工具行：插图片
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        imgBtn = new Button(this);
        imgBtn.setText("📷 插入图片");
        imgBtn.setOnClickListener(v -> showImageSourceDialog());
        tools.addView(imgBtn, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(tools);

        saveBtn = new Button(this);
        saveBtn.setText("保存并上传");
        saveBtn.setOnClickListener(v -> save());
        root.addView(saveBtn);

        setContentView(root);

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

    // ---------- 预览 ----------

    void togglePreview() {
        previewing = !previewing;
        if (previewing) {
            renderPreview();
            bodyField.setVisibility(View.GONE);
            if (fileField != null) fileField.setVisibility(View.GONE);
            imgBtn.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            toggleBtn.setText("编辑");
        } else {
            webView.setVisibility(View.GONE);
            bodyField.setVisibility(View.VISIBLE);
            if (fileField != null) fileField.setVisibility(View.VISIBLE);
            imgBtn.setVisibility(View.VISIBLE);
            toggleBtn.setText("预览");
        }
    }

    void renderPreview() {
        try {
            InputStream is = getResources().openRawResource(R.raw.preview);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] t = new byte[8192];
            int n;
            while ((n = is.read(t)) > 0) buf.write(t, 0, n);
            is.close();
            String tpl = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            String siteBase = "https://" + owner + ".github.io/";
            String json = JSONObject.quote(bodyField.getText().toString());
            String html = tpl.replace("__SITE_BASE__", siteBase).replace("__MD_JSON__", json);
            webView.loadDataWithBaseURL(siteBase, html, "text/html", "utf-8", null);
        } catch (Exception e) {
            status.setText("预览失败: " + e.getMessage());
        }
    }

    // ---------- 拉取原文 ----------

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

    // ---------- 插入图片 ----------

    void showImageSourceDialog() {
        String[] options = {"从相册选择", "拍照"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("插入图片")
                .setItems(options, (d, w) -> {
                    if (w == 0) {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        startActivityForResult(Intent.createChooser(i, "选择图片"), REQ_GALLERY);
                    } else {
                        try {
                            ContentValues v = new ContentValues();
                            v.put(MediaStore.Images.Media.DISPLAY_NAME,
                                    "cam_" + System.currentTimeMillis() + ".jpg");
                            pendingCameraUri = getContentResolver().insert(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                            startActivityForResult(i, REQ_CAMERA);
                        } catch (Exception e) {
                            Toast.makeText(this, "无法打开相机: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        Uri uri = null;
        if (req == REQ_GALLERY && data != null && data.getData() != null) {
            uri = data.getData();
        } else if (req == REQ_CAMERA) {
            uri = pendingCameraUri;
            pendingCameraUri = null;
        }
        if (uri != null) uploadImage(uri);
    }

    /** 当前文章对应的图片目录名（不含扩展名的文件名） */
    String imageSlug() {
        String fname = isNew ? fileField.getText().toString().trim() : name;
        if (fname.endsWith(".md")) fname = fname.substring(0, fname.length() - 3);
        if (fname.isEmpty()) fname = "mobile";
        // 去掉非法字符
        return fname.replaceAll("[\\\\/:*?\"<>|\\s]", "-");
    }

    void uploadImage(Uri uri) {
        pendingInsertPos = bodyField.getSelectionEnd();
        if (pendingInsertPos < 0) pendingInsertPos = bodyField.length();
        final String slug = imageSlug();
        final String fname = "img_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".jpg";
        status.setText("正在处理图片…");
        imgBtn.setEnabled(false);

        new Thread(() -> {
            try {
                byte[] bytes = readCompressed(uri);
                JSONObject body = new JSONObject();
                body.put("message", "上传图片: " + slug + "/" + fname + "（手机端）");
                body.put("content", Base64.encodeToString(bytes, Base64.NO_WRAP));
                body.put("branch", branch);
                String url = "https://api.github.com/repos/" + owner + "/" + repo
                        + "/contents/" + GitHubApi.encPath("source/images/" + slug + "/" + fname);
                GitHubApi.request("PUT", url, token, body);
                String mdRef = "![](/images/" + slug + "/" + fname + ")";
                runOnUiThread(() -> {
                    imgBtn.setEnabled(true);
                    int pos = Math.min(pendingInsertPos, bodyField.length());
                    bodyField.getText().insert(pos,
                            (pos > 0 && bodyField.getText().charAt(pos - 1) != '\n' ? "\n" : "")
                                    + mdRef);
                    status.setText("图片已上传: " + slug + "/" + fname);
                    Toast.makeText(this, "图片已上传并插入", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    imgBtn.setEnabled(true);
                    status.setText("图片上传失败: " + e.getMessage());
                });
            }
        }).start();
    }

    /** 读取图片并压缩到最长边 ≤1600px 的 JPEG（质量 85） */
    byte[] readCompressed(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("无法读取所选图片");
        BitmapFactory.decodeStream(in, null, o);
        in.close();

        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1600 || o.outHeight / (sample * 2) >= 1600) {
            sample *= 2;
        }
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        in = getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
        if (in != null) in.close();
        if (bmp == null) throw new Exception("图片解码失败");

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bo);
        return bo.toByteArray();
    }

    // ---------- 保存文章 ----------

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
