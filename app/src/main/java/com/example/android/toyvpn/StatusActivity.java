package com.example.android.toyvpn;

import android.os.Bundle;
import android.os.CountDownTimer;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.optman.minivtun.Native;

public class StatusActivity extends AppCompatActivity {

  protected CountDownTimer timer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    RelativeLayout relParent = new RelativeLayout(this);
    RelativeLayout.LayoutParams relParentParam = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
    relParent.setLayoutParams(relParentParam);

    TextView txtView = new TextView(this);
    RelativeLayout.LayoutParams txtViewParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
    txtView.setLayoutParams(txtViewParams);
    txtViewParams.addRule(RelativeLayout.ALIGN_TOP);

    relParent.addView(txtView);
    setContentView(relParent, relParentParam);

    // stop after 10 minutes to save battery.
    timer = new CountDownTimer(600_000, 1_000) {
      public void onTick(long millisUntilFinished) {
        String status = Native.Info();
        txtView.setText(status);
      }

      public void onFinish() {
      }
    };
    timer.start();
  }

  @Override
  protected void onDestroy() {
    timer.cancel();
    super.onDestroy();
  }

}
