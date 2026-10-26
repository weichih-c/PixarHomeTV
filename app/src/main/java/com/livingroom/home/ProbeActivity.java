package com.livingroom.home;

import android.app.Activity;
import android.os.Bundle;

/** Opens and closes at once; see HomeRedirectService.probeAtBoot. */
public class ProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
        overridePendingTransition(0, 0);
    }
}
