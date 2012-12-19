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
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.widget.Toast;

class TransferDataTask extends AsyncTask<String, Integer, byte[]> {

    private ProgressDialog mProgressDialog;
    private ScrapeActivity mActivity;
    private byte[] mBuf = new byte[4096];

    public static final int SNIP_SIZE = 1024;

    private File mInFile, mLootFile;
    private int mMBs;

    public TransferDataTask(ScrapeActivity activity) {
        mActivity = activity;
    }

    @Override
    protected void onPreExecute() {
        mActivity.setProgressBarIndeterminateVisibility(true);

        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setMessage("Scraping...");
        mProgressDialog.setCancelable(false);
    }

    // Input, Output, MBs
    @Override
    protected byte[] doInBackground(String... params) {
        if (params.length != 3)
            return new byte[0];

        mInFile = new File(params[0]);
        mLootFile = new File(params[1]);
        mMBs = Integer.parseInt(params[2]);
        long transfered = transfer(mInFile, mLootFile, mMBs);
        int snipSize = (int) Math.min(transfered, 1024);

        if (transfered < 0)
            return null;

        byte[] snip = new byte[snipSize];
        if (snipSize > 0)
            System.arraycopy(mBuf, 0, snip, 0, snipSize);
        return snip;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {

        if (!mProgressDialog.isShowing())
            mProgressDialog.show();
        
        mProgressDialog.setProgress(progress[0]);
        // Log.i(ScrapeActivity.LOGTAG, "TestKeysTask: progress update " +
        // progress[0]);
    }

    @Override
    protected void onPostExecute(byte[] snip) {
        // Log.i(SLURPActivity.LOGTAG, "ReadTagTask: onPostExecute");

        if (mProgressDialog.isShowing()) {
            mActivity.setProgressBarIndeterminateVisibility(false);
            mProgressDialog.dismiss();
        }

        if (snip == null) {
            Toast.makeText(mActivity, "Couldn't read data", Toast.LENGTH_SHORT).show();
        } else if (snip.length == 0) {
            Toast.makeText(mActivity, "No data in file", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(mActivity, "Data written to:\n" + mLootFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            mActivity.setSnipData(snip);
        }
   }

    private long transfer(File in, File out, int maxMBs) {

        FileOutputStream fos = null;
        FileInputStream fis = null;

        long maxBytesToTransfer = 1024 * 1024 * maxMBs;
        long transferedBytes = 0;

        try {
            try {
                fos = new FileOutputStream(out);
                fis = new FileInputStream(in);

                int read = fis.read(mBuf, 0, mBuf.length);
                while (read > 0 && transferedBytes < maxBytesToTransfer) {
                    
                    // Don't send progress on first iteration
                    if (transferedBytes >= mBuf.length)
                        publishProgress((int) (100 * (transferedBytes + 1) / maxBytesToTransfer));

                    fos.write(mBuf, 0, read);
                    transferedBytes += read;
                    read = fis.read(mBuf, 0, mBuf.length);
                }

            } finally {
                if (fis != null)
                    fis.close();
                if (fos != null)
                    fos.close();
            }

        } catch (Exception e) {
            out.delete();
            return -1;
        }

        return transferedBytes;
    }

}