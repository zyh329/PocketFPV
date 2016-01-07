package com.tairtech.pocketfpv;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //UmengUpdateAgent.setUpdateOnlyWifi(false);  //无论什么网络都去更新
        UmengUpdateAgent.update(this);  //一打开软件就检查更新
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);    //umeng tngji
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);   //umeng tongji
    }
}
