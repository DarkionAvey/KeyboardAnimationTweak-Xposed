package net.darkion.keyboardanimationfix;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    ComponentName mActivityAlias;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mActivityAlias = new ComponentName(this, "net.darkion.keyboardanimationfix.MainActivityAlias");
        updateLauncherIconStatusText();
    }

    private void updateLauncherIconStatusText() {
        TextView textView = findViewById(R.id.textView4);
        textView.setText((isLauncherIconVisible() ? "Disable" : "Enable") + " launcher icon");
    }

    public void disableLauncherIcon(View v) {
        getPackageManager().setComponentEnabledSetting(mActivityAlias, isLauncherIconVisible() ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        Toast.makeText(this, !isLauncherIconVisible() ? "Disabled" : "Enabled", Toast.LENGTH_SHORT).show();
        updateLauncherIconStatusText();
    }

    private boolean isLauncherIconVisible() {
        return getPackageManager().getComponentEnabledSetting(mActivityAlias) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

}
