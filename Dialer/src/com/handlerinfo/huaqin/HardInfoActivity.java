package com.hardinfo.huaqin;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import android.util.Log;

import com.android.dialer.R;

/**
 * Created by shenlong on 17-4-19.
 */

public class HardInfoActivity extends Activity {

    private ListView mLvInfo;
    private SimpleAdapter mListAdapter;

    private static final String ITEM_NAME = "name";
    private static final String ITEM_INFO = "info";
    private ArrayList<HashMap<String, String>> mListInfoData = new ArrayList<HashMap<String, String>>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hardinfo);
        initView();
    }

    private void initView() {
        if (!initData()) {
            return;
        }

        mLvInfo = (ListView) findViewById(R.id.lv_info);
        mListAdapter = new SimpleAdapter(
                this,
                mListInfoData,
                R.layout.hardinfo_list_item,
                new String[]{ITEM_NAME, ITEM_INFO},
                new int[]{R.id.tv_name, R.id.tv_info});

        mLvInfo.setAdapter(mListAdapter);
    }

    /**
     * <p>
     * Description:初始化列表数据
     * <p>
     *
     * @return 硬件信息文件可能不存在
     */
    private boolean initData() {
//        String[] cmds = getResources().getStringArray(R.array.commands);
        String cmd = "cat /sys/class/huaqin/interface/hw_info/hw_summary";
        String result;
        String[] infos;
//        for (String cmd : cmds) {
        try {
            result = com.hardinfo.huaqin.ShellUtil.execCommand(cmd);
            Log.i("HardInfoActivity", "result=" + result);
            if (result == null || result.length() == 0) {
                return false;
            }

            //将设备名和信息进行分段显示
            infos = result.split("\n");
            Log.i("HardInfoActivity", "infos.length=" + infos.length + ",infos=" + infos);

            for (String info : infos) {
                if (!TextUtils.isEmpty(info)) {
                    HashMap<String, String> map = new HashMap<String, String>();
                    String[] nameAndInfo = info.split(":");
                    map.put(ITEM_NAME, nameAndInfo[0]);
                    map.put(ITEM_INFO, info.substring(nameAndInfo[0].length() + 1));
                    mListInfoData.add(map);
                }

            }
//            if ((result.indexOf("ctp modlue") != -1)) {
//                HashMap<String, String> map1 = new HashMap<String, String>();
//                String marray[] = result.split("ctp modlue");
//                String mtest[] = marray[0].split(":");
//                String mtest2[] = marray[1].split(":");
//                map.put(ITEM_NAME, infos[0]);
//                map.put(ITEM_INFO, mtest[1]);
//                mListInfoData.add(map);
//                map1.put(ITEM_NAME, "ctp modlue");
//                if (mtest2.length > 1) {
//                    map1.put(ITEM_INFO, mtest2[1]);
//                } else {
//                    map1.put(ITEM_INFO, "");
//                }
//                mListInfoData.add(map1);
//            } else {
//                map.put(ITEM_NAME, infos[0]);
//                map.put(ITEM_INFO, result.substring(infos[0].length() + 1));
//                mListInfoData.add(map);
//            }
        } catch (IOException e) {
            e.printStackTrace();
        }
//        }
        if (mListInfoData.size() == 0) {
            Toast.makeText(this, getString(R.string.msg_no_info), Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }
}
