package com.youwenshuai.blogwriter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends Activity {

    SharedPreferences prefs;
    ListView list;
    ProgressBar progress;
    TextView status;
    ArrayAdapter<String> adapter;
    final ArrayList<String> names = new ArrayList<>();
    final ArrayList<String> shas = new ArrayList<>();

    String token, owner, repo, branch, dir;

    void loadCfg() {
        token = Crypto.getToken(prefs);
        owner = prefs.getString("owner", "youwenshuai");
        repo = prefs.getString("repo", "youwenshuai.github.io");
        branch = prefs.getString("branch", "hexo-source");
        dir = prefs.getString("dir", "source/_posts");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        loadCfg();

        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("博客文章");
        title.setTextSize(22);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> loadPosts());
        bar.addView(refresh);
        Button settings = new Button(this);
        settings.setText("设置");
        settings.setOnClickListener(v -> showSettings());
        bar.addView(settings);
        root.addView(bar);

        status = new TextView(this);
        status.setPadding(0, (int) (8 * dp), 0, 0);
        status.setTextSize(13);
        root.addView(status);

        Button newBtn = new Button(this);
        newBtn.setText("＋ 写新文章");
        newBtn.setOnClickListener(v -> openEditor(null, null));
        root.addView(newBtn);

        progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        list.setAdapter(adapter);
        setContentView(root);

        list.setOnItemClickListener((parent, view, pos, id) ->
                openEditor(names.get(pos), shas.get(pos)));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            confirmDelete(names.get(pos), shas.get(pos));
            return true;
        });

        if (token.isEmpty()) {
            showSettings();
        } else {
            loadPosts();
        }
    }

    void openEditor(String name, String sha) {
        Intent it = new Intent(this, EditorActivity.class);
        it.putExtra("name", name);
        it.putExtra("sha", sha);
        startActivity(it);
    }

    void loadPosts() {
        status.setText("加载中…");
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String url = "https://api.github.com/repos/" + owner + "/" + repo
                        + "/contents/" + GitHubApi.encPath(dir)
                        + "?ref=" + GitHubApi.enc(branch);
                String resp = GitHubApi.request("GET", url, token, null);
                JSONArray arr = new JSONArray(resp);
                names.clear();
                shas.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject f = arr.getJSONObject(i);
                    String n = f.optString("name", "");
                    if (n.endsWith(".md") || n.endsWith(".markdown")) {
                        names.add(n);
                        shas.add(f.getString("sha"));
                    }
                }
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    status.setText("共 " + names.size() + " 篇文章（长按可删除）");
                    progress.setVisibility(View.GONE);
                    checkDeployStatus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("加载失败: " + e.getMessage());
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "检查网络或设置里的 Token", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** 查询最新一次云端构建（Actions）状态，附加在状态栏后面 */
    void checkDeployStatus() {
        final String base = status.getText().toString();
        new Thread(() -> {
            try {
                String url = "https://api.github.com/repos/" + owner + "/" + repo
                        + "/actions/runs?per_page=1";
                JSONObject obj = new JSONObject(GitHubApi.request("GET", url, token, null));
                JSONObject r = obj.getJSONArray("workflow_runs").getJSONObject(0);
                String st = r.optString("status", "");
                String cc = r.optString("conclusion", "");
                String txt;
                if ("in_progress".equals(st) || "queued".equals(st) || "pending".equals(st)) {
                    txt = "｜ ☁️ 云端构建中…";
                } else if ("success".equals(cc)) {
                    txt = "｜ ✅ 已发布";
                } else if ("failure".equals(cc)) {
                    txt = "｜ ❌ 构建失败";
                } else {
                    txt = "｜ 云端: " + (cc.isEmpty() ? st : cc);
                }
                runOnUiThread(() -> status.setText(base + txt));
            } catch (Exception ignored) {
            }
        }).start();
    }

    void confirmDelete(String name, String sha) {
        new AlertDialog.Builder(this)
                .setTitle("删除文章")
                .setMessage("确定删除 " + name + " 吗？会同时从仓库中删除。")
                .setPositiveButton("删除", (d, w) -> deletePost(name, sha))
                .setNegativeButton("取消", null)
                .show();
    }

    void deletePost(String name, String sha) {
        status.setText("正在删除 " + name + " …");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("message", "删除文章: " + name + "（手机端）");
                body.put("sha", sha);
                body.put("branch", branch);
                String url = "https://api.github.com/repos/" + owner + "/" + repo
                        + "/contents/" + GitHubApi.encPath(dir + "/" + name);
                GitHubApi.request("DELETE", url, token, body);
                // 本地草稿也一并清理
                try {
                    new java.io.File(getFilesDir(), "drafts/" + EditorActivity.sanitize(name) + ".txt")
                            .delete();
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "已删除，云端将自动重新发布", Toast.LENGTH_SHORT).show();
                    loadPosts();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("删除失败: " + e.getMessage()));
            }
        }).start();
    }

    void showSettings() {
        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * dp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        TextView hint = new TextView(this);
        hint.setText("需要 GitHub Token（Fine-grained，勾选该仓库的 Contents 读写权限）。Token 将加密保存在本机。");
        hint.setTextSize(12);
        box.addView(hint);

        EditText eToken = new EditText(this);
        eToken.setHint("GitHub Token");
        eToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String cur = Crypto.getToken(prefs);
        if (!cur.isEmpty()) eToken.setText(cur);
        box.addView(eToken);

        EditText eOwner = new EditText(this);
        eOwner.setHint("仓库所有者（用户名）");
        eOwner.setText(prefs.getString("owner", "youwenshuai"));
        box.addView(eOwner);

        EditText eRepo = new EditText(this);
        eRepo.setHint("仓库名");
        eRepo.setText(prefs.getString("repo", "youwenshuai.github.io"));
        box.addView(eRepo);

        EditText eBranch = new EditText(this);
        eBranch.setHint("源码分支");
        eBranch.setText(prefs.getString("branch", "hexo-source"));
        box.addView(eBranch);

        EditText eDir = new EditText(this);
        eDir.setHint("文章目录");
        eDir.setText(prefs.getString("dir", "source/_posts"));
        box.addView(eDir);

        new AlertDialog.Builder(this)
                .setTitle("GitHub 设置")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    Crypto.saveToken(prefs, eToken.getText().toString().trim());
                    prefs.edit()
                            .putString("owner", eOwner.getText().toString().trim())
                            .putString("repo", eRepo.getText().toString().trim())
                            .putString("branch", eBranch.getText().toString().trim())
                            .putString("dir", eDir.getText().toString().trim())
                            .apply();
                    loadCfg();
                    if (!token.isEmpty()) loadPosts();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
