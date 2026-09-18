package com.luoqi.tuoke;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.luoqi.tuoke.databinding.ActivityLoginBinding;

/**
 * 登录页：输入卡密 -> 泡椒云验证 -> 成功后进入主界面。
 *
 * 关键：登录成功后把卡密明文落盘（PjYunAuth.saveCard 内部做），
 * 后续心跳/解绑都靠这份卡密，避免 StarCut 的历史缺陷。
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.tvVersion.setText("v" + BuildConfig.VERSION_NAME);

        // 已登录过：自动尝试进入主界面（先做心跳校验由 MainActivity 负责）
        String savedCard = PjYunAuth.cardFromPrefs(this);
        if (!TextUtils.isEmpty(savedCard)) {
            b.etCard.setText(savedCard);
        }

        b.btnLogin.setOnClickListener(v -> doLogin());
        b.tvUnbind.setOnClickListener(v -> doUnbind());
    }

    private void doLogin() {
        String card = b.etCard.getText() == null ? "" : b.etCard.getText().toString().trim();
        if (TextUtils.isEmpty(card)) {
            toast("请输入卡密");
            return;
        }
        setBusy(true, "验证中…");
        PjYunAuth.login(this, card, new PjYunAuth.Callback() {
            @Override
            public void onOk(org.json.JSONObject data) {
                setBusy(false, "登录成功");
                toast("登录成功");
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFail(String msg) {
                setBusy(false, "登录失败：" + msg);
                toast(msg);
            }
        });
    }

    private void doUnbind() {
        if (TextUtils.isEmpty(PjYunAuth.cardFromPrefs(this))) {
            toast("本地无已登录卡密");
            return;
        }
        setBusy(true, "解绑中…");
        PjYunAuth.logout(this, new PjYunAuth.Callback() {
            @Override
            public void onOk(org.json.JSONObject data) {
                setBusy(false, "已解绑");
                b.etCard.setText("");
                toast("解绑成功");
            }

            @Override
            public void onFail(String msg) {
                setBusy(false, "解绑结果：" + msg);
                b.etCard.setText("");
                toast(msg);
            }
        });
    }

    private void setBusy(boolean busy, String status) {
        b.btnLogin.setEnabled(!busy);
        b.tvUnbind.setEnabled(!busy);
        if (status != null) b.tvStatus.setText(status);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
