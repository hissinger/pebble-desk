// 홈페이지(site/img/apps)의 지원 앱 아이콘: 에뮬에 깔린 앱의 아이콘을 가장 큰 밀도(640dpi) 그림으로 PNG 로 그린다.
// 에뮬은 300dpi 라 그냥 꺼내면 xhdpi(약 96px) 그림을 늘려 흐려진다.
//   javac --release 8 -cp android.jar IconDump.java && d8 --min-api 30 --lib android.jar IconDump.class
//   adb push classes.dex /data/local/tmp/icondump.dex
//   adb shell mkdir -p /data/local/tmp/icons
//   adb shell CLASSPATH=/data/local/tmp/icondump.dex app_process / IconDump 384 kr.co.kyobobook.KEL kr.co.millie.millieshelf:ic_launcher …
// 패키지 뒤 `:이름` 은 앱 아이콘 대신 그 mipmap(밀리 휴대폰판은 기념 아이콘이 걸려 있어 기본 `ic_launcher`).
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import java.io.FileOutputStream;

public class IconDump {

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Constructor<?> c = at.getDeclaredConstructor();
        c.setAccessible(true);
        Context ctx = (Context) at.getDeclaredMethod("getSystemContext").invoke(c.newInstance());
        PackageManager pm = ctx.getPackageManager();
        int px = Integer.parseInt(args[0]);
        for (int i = 1; i < args.length; i++) {
            String[] p = args[i].split(":");
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p[0], 0);
                Resources r = pm.getResourcesForApplication(ai);
                int id = p.length > 1 ? r.getIdentifier(p[1], "mipmap", p[0]) : ai.icon;
                Drawable d = r.getDrawableForDensity(id, 640, null);
                Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, px, px);
                d.draw(new Canvas(b));
                try (FileOutputStream o = new FileOutputStream("/data/local/tmp/icons/hi-" + p[0] + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
                System.out.println("ok " + p[0] + " " + d.getIntrinsicWidth() + "px " + d.getClass().getSimpleName());
            } catch (Exception e) { System.out.println("fail " + p[0] + " " + e); }
        }
    }
}
