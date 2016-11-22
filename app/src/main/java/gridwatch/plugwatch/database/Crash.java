package gridwatch.plugwatch.database;

import com.loopj.android.http.RequestParams;

import io.realm.RealmObject;

/**
 * Created by nklugman on 6/30/16.
 */
public class Crash extends RealmObject {

    private long mTime;
    private String mText;
    private String mGWID;
    private String mGroupID;

    public Crash() {

    }

    public Crash(long time, String text, String gwid, String groupid) {
        mTime = time;
        mText = text;
        mGWID = gwid;
        mGroupID = groupid;
    }

    public RequestParams toRequestParams() {
        RequestParams values = new RequestParams();
        values.put("type", "crash");
        values.put("time", mTime);
        values.put("text", mText);
        values.put("id", mGWID);
        values.put("group", mGroupID);
        values.put("wifi", "no");
        return values;
    }
}
