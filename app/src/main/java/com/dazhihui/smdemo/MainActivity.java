package com.dazhihui.smdemo;

import android.app.ProgressDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dazhihui.smdemo.network.NetWorkManager;
import com.dazhihui.smdemo.trade.TradePack;
import com.dazhihui.smdemo.trade.sm.SMHelper;

public class MainActivity extends AppCompatActivity implements MainContract.View{

    private MainContract.Presenter mPresenter;

    public static final String QSFlag = "华福证券";

    ProgressDialog mProgressDialog;

    private Button btnAE,btnA0,btnAC;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnAE = (Button) findViewById(R.id.btnAe);
        btnA0 = (Button) findViewById(R.id.btnA0);
        btnAC = (Button) findViewById(R.id.btnAc);
        textView = (TextView) findViewById(R.id.tv);

        btnAE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPresenter.sendAE();
            }
        });

        btnA0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPresenter.sendA0();
            }
        });

        btnAC.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPresenter.sendAC();
            }
        });

        new MainPresenter(this);
    }

    @Override
    public void setLoadingIndicator(boolean active) {
        if(mProgressDialog == null){
            mProgressDialog = new ProgressDialog(this);
        }
        if(active){
            mProgressDialog.show();
        }else {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void setPresenter(MainContract.Presenter presenter) {
        mPresenter = presenter;
    }

    @Override
    public void showMessage(String description) {
        Toast toast = Toast.makeText(this,description,Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER,0,0);
        toast.show();
    }

    @Override
    public void showResult(String result) {
        StringBuilder sb = new StringBuilder(textView.getText());
        sb.append("------------------------------------------------")
            .append("\n")
            .append(result)
            .append("\n");
        textView.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetWorkManager.getInstance().close();
        TradePack.clear();
        SMHelper.clear();
    }
}
