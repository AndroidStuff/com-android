package sid.comslav.com.circleofmusic.helpers;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.UploadNotificationConfig;

public class uploadHelper extends AsyncTask {
    private String path;
    private Context mContext;

    public uploadHelper(Context context, String filename) {
        path = filename;
        mContext = context;
    }

    @Override
    protected Object doInBackground(Object[] params) {
        String url = "http://circleofmusic-sidzi.rhcloud.com/upYourTrack";
        try {
            final String uploadIdentity =
                    new MultipartUploadRequest(mContext, url)
                            .addFileToUpload(path, "file")
                            .setNotificationConfig(new UploadNotificationConfig())
                            .setMaxRetries(2)
                            .startUpload();
        } catch (Exception exc) {
            Log.e("AndroidUploadService", exc.getMessage(), exc);
        }
        return null;
    }
}