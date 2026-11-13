package com.youwenshuai.blogwriter;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** 用 Android Keystore 里的 AES-GCM 密钥加解密 GitHub Token */
public class Crypto {

    static final String KS = "AndroidKeyStore";
    static final String ALIAS = "blogwriter_token";

    static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        SecretKey k = (SecretKey) ks.getKey(ALIAS, null);
        if (k == null) {
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KS);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            k = kg.generateKey();
        }
        return k;
    }

    static String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    static String decrypt(String enc) throws Exception {
        byte[] all = Base64.decode(enc, Base64.NO_WRAP);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
        return new String(c.doFinal(all, 12, all.length - 12), "UTF-8");
    }

    /** 读取 Token：优先解密 token_enc；兼容旧明文 token 并自动迁移为加密存储 */
    public static String getToken(SharedPreferences prefs) {
        try {
            String enc = prefs.getString("token_enc", null);
            if (enc != null && !enc.isEmpty()) return decrypt(enc);
        } catch (Exception ignored) {
        }
        String plain = prefs.getString("token", "");
        if (plain != null && !plain.isEmpty()) {
            saveToken(prefs, plain);
        }
        return plain == null ? "" : plain;
    }

    /** 加密保存 Token，同时清掉可能存在的明文 */
    public static void saveToken(SharedPreferences prefs, String plain) {
        SharedPreferences.Editor ed = prefs.edit();
        try {
            if (plain != null && !plain.isEmpty()) {
                ed.putString("token_enc", encrypt(plain));
            } else {
                ed.remove("token_enc");
            }
        } catch (Exception e) {
            // Keystore 极端异常时退回明文，保证功能可用
            ed.putString("token", plain == null ? "" : plain);
        }
        ed.remove("token");
        ed.apply();
    }
}
