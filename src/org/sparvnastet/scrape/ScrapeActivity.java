/**
 * Copyright (c) 2011 Anders Sundman <anders@4zm.org>
 *
 * This file is part of sCRAPe.
 *
 * sCRAPe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * sCRAPe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with sCRAPe. If not, see <http://www.gnu.org/licenses/>.
 */

package org.sparvnastet.scrape;

import java.io.File;
import java.util.ArrayList;

import org.sparvnastet.scrap.R;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ScrapeActivity extends Activity {
    private Spinner mSpinner;
    private ArrayList<File> mFiles;
    private Button mScrapeBtn;
    private TextView mText;
    private EditText mMaxSize;
    private EditText mSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_scrape);
        Log.i("sCRAPe", "enter onCreate");

        mSearch = (EditText) findViewById(R.id.search_edit);
        mSpinner = (Spinner) findViewById(R.id.file_spinner);
        mScrapeBtn = (Button) findViewById(R.id.scrape_btn);
        mText = (TextView) findViewById(R.id.txt);
        mMaxSize = (EditText) findViewById(R.id.max_size_edit);
        setupSpinner();

        Log.i("sCRAPe", "exit onCreate");
    }

    private void setupSpinner() {
        File devDir = new File("/dev");

        mFiles = new ArrayList<File>();
        CompileFileList(mFiles, devDir);

        ArrayList<String> fileDescList = new ArrayList<String>();
        for (int i = 0; i < mFiles.size(); ++i) {
            String s = "[" + (mFiles.get(i).canRead() ? "r" : "-") + (mFiles.get(i).canWrite() ? "w" : "-")
                    + (mFiles.get(i).canExecute() ? "x" : "-") + "] " + mFiles.get(i).getAbsolutePath();
            fileDescList.add(s);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                fileDescList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(adapter);
        mSpinner.setSelected(false);
        mSpinner.setOnItemSelectedListener(new SpinnerSelectionListener());
        mSpinner.setSelection(0);
    }

    private void CompileFileList(ArrayList<File> list, File root) {
        File[] files = root.listFiles();
        for (int i = 0; i < files.length; ++i) {
            if (files[i].isDirectory() && files[i].canRead()) {
                CompileFileList(list, files[i]);
            } else if (files[i].canRead()) {
                list.add(files[i]);
            }
        }
    }

    public void scrapeClicked(View view) {

        long megs = Integer.parseInt(mMaxSize.getText().toString());

        if (megs < 1 || megs > 4096) {
            mText.setText("");
            Toast.makeText(this, "Max file size must be in [1,4096] MB range.", Toast.LENGTH_SHORT).show();
            return;
        }

        File inFile = mFiles.get(mSpinner.getSelectedItemPosition());

        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File(sdCard.getAbsolutePath() + "/sCRAPe");
        dir.mkdirs();
        File lootFile = new File(dir, "loot.dat");

        mText.setText("");

        TransferDataTask tdt = new TransferDataTask(this);
        tdt.execute(inFile.getAbsolutePath(), lootFile.getAbsolutePath(), mMaxSize.getText().toString(), mSearch
                .getText().toString());
    }

    public void setSnipData(byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append(bytesToHexAndASCII(data, data.length));
        if (data.length == TransferDataTask.SNIP_SIZE)
            sb.append("... [snip] ...");

        mText.setText(sb);
    }

    public void setSearchData(ArrayList<byte[]> matches) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < matches.size(); i++) {
            sb.append("MATCH #" + i + "\n");
            sb.append("================================================\n");
            sb.append(bytesToHexAndASCII(matches.get(i), matches.get(i).length));
            sb.append("================================================\n\n");
        }

        mText.setText(sb);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_scrape, menu);
        return true;
    }

    private String byteToHexString(byte b) {
        String hex = Integer.toHexString(b & 0xff);
        return (b & 0xff) < 0x10 ? "0" + hex : hex;
    }

    private String bytesToHexAndASCII(byte[] bytes, int len) {
        if (len < 1)
            return "No Data";

        StringBuilder sb = new StringBuilder();

        int paddedLen = ((len + 15) / 16) * 16;

        for (int i = 0; i <= paddedLen; ++i) {
            if (i != 0 && i % 8 == 0) {
                sb.append(" ");
            }

            if (i != 0 && i % 16 == 0) {
                sb.append(" ");
                for (int j = i - 16; j < i && j < len; ++j) {
                    sb.append(bytes[j] < 32 || bytes[j] > 126 ? '.' : (char) bytes[j]);
                }
                sb.append("\n");
            }

            if (i == paddedLen)
                return sb.toString();

            if (i >= len) {
                sb.append("..");
            } else {
                sb.append(byteToHexString(bytes[i]));
            }
            sb.append(" ");
        }

        return sb.toString();
    }

    private class SpinnerSelectionListener implements OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            mScrapeBtn.setEnabled(parent.getItemAtPosition(pos).toString().startsWith("[r"));
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            mScrapeBtn.setEnabled(false);
        }
    }
}
