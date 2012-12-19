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
import java.util.ArrayList;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.widget.Toast;

class TransferDataTask extends AsyncTask<String, Integer, ArrayList<byte[]>> {

    private final int MAX_SNIP_SIZE = 512;
    private final int BUF_SIZE = 4096;
    private ProgressDialog mProgressDialog;
    private ScrapeActivity mActivity;
    private byte[] mBuf = new byte[BUF_SIZE];
    private String mSearchPattern;
    private ArrayList<byte[]> mSearchMatches = new ArrayList<byte[]>();

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

    // Input, Output, MBs, search
    @Override
    protected ArrayList<byte[]> doInBackground(String... params) {
        if (params.length != 4)
            return null;

        mInFile = new File(params[0]);
        mLootFile = new File(params[1]);
        mMBs = Integer.parseInt(params[2]);
        mSearchPattern = params[3];
        long transfered = transfer(mInFile, mLootFile, mMBs, mSearchPattern);

        if (transfered < 0)
            return null;

        return mSearchMatches;
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {

        if (!mProgressDialog.isShowing())
            mProgressDialog.show();

        mProgressDialog.setProgress(progress[0]);
    }

    @Override
    protected void onPostExecute(ArrayList<byte[]> searchMatches) {
        if (mProgressDialog.isShowing()) {
            mActivity.setProgressBarIndeterminateVisibility(false);
            mProgressDialog.dismiss();
        }

        if (searchMatches == null) {
            Toast.makeText(mActivity, "Couldn't read data", Toast.LENGTH_SHORT).show();
        } else if (searchMatches.size() == 0) {
            Toast.makeText(mActivity, "No data in file", Toast.LENGTH_SHORT).show();
        } else if (searchMatches.size() == 1) {
            if (!mSearchPattern.isEmpty())
                Toast.makeText(mActivity, "Search string not found", Toast.LENGTH_SHORT).show();
            mActivity.setSnipData(mSearchMatches.get(0));
        } else {
            Toast.makeText(mActivity, "Data written to:\n" + mLootFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            mSearchMatches.remove(0); // Delete the snip data
            mActivity.setSearchData(mSearchMatches);
        }
    }

    private long transfer(File in, File out, int maxMBs, String searchPatern) {

        final int SEARCH_PADDING = 64;

        FileOutputStream fos = null;
        FileInputStream fis = null;

        long maxBytesToTransfer = 1024 * 1024 * maxMBs;
        long transferedBytes = 0;

        try {
            try {
                fos = new FileOutputStream(out);
                fis = new FileInputStream(in);

                int read = fis.read(mBuf, 0, mBuf.length);

                if (read >= 0) {
                    int snipSize = Math.min(read, MAX_SNIP_SIZE);
                    byte[] snip = new byte[snipSize];
                    System.arraycopy(mBuf, 0, snip, 0, snipSize);
                    mSearchMatches.add(snip);
                }

                while (read > 0 && transferedBytes < maxBytesToTransfer) {

                    // TODO: Impl. proper search - this won't work on buffer
                    // boundary
                    if (!searchPatern.isEmpty()) {
                        String str = new String(mBuf, "ISO-8859-1");

                        int start = 0;
                        while (start != -1) {
                            int matchIndex = str.indexOf(searchPatern, start);
                            start = matchIndex;
                            if (matchIndex != -1) {
                                int paddedFirst = Math.max(0, (matchIndex - SEARCH_PADDING));
                                int paddedLast = Math
                                        .min(read - 1, matchIndex + searchPatern.length() + SEARCH_PADDING);
                                byte[] match = new byte[paddedLast - paddedFirst + 1];
                                System.arraycopy(mBuf, paddedFirst, match, 0, paddedLast - paddedFirst + 1);
                                mSearchMatches.add(match);
                                start += searchPatern.length();
                            }
                        }
                    }

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
