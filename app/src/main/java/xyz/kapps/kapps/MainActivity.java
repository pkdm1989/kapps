package xyz.kapps.kapps;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.SyncStateContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements
        AdapterView.OnItemClickListener {

    ArrayList<String> itemNames = new ArrayList<String>();
    ArrayList<String> itemLinks = new ArrayList<String>();
    int position;
    private BroadcastReceiver upgradeReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        if(Build.VERSION.SDK_INT>=24) {
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }

        Document doc;
        try {

            doc = Jsoup.connect("https://kapps.xyz/list.php").get();

            // get all links
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String url = link.attr("href").replace("apks", "https://kapps.xyz/apks");

                itemNames.add(link.text()+" - "+"click me");
                itemLinks.add(url);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        ArrayAdapter<String> itemsAdapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, itemNames);

        ListView listView = (ListView) findViewById(R.id.lvItems);
        listView.setAdapter(itemsAdapter);
        listView.setOnItemClickListener(this);

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE}, 23);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        this.position = position;
        String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
        String fileName = "tempkapps"+position+".apk";
        destination += fileName;
        final Uri uri = Uri.parse("file://" + destination);

        //Delete update file if exists
        File file = new File(destination);
        if (file.exists())
            file.delete();

        //get url of app on server
        String url = itemLinks.get(position);

        if (false){//Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        //set downloadmanager
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("Downloading "+itemNames.get(position).split(" - ")[0]+" ...");
        request.setTitle("tempkapps"+position);

        //set destination
        request.setDestinationUri(uri);

        // get download service and enqueue file
        final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);


            //set BroadcastReceiver to install app when .apk is downloaded
            final BroadcastReceiver onComplete = new BroadcastReceiver() {
                public void onReceive(Context ctxt, Intent intent) {
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    install.setDataAndType(uri,
                            manager.getMimeTypeForDownloadedFile(downloadId));
                    startActivityForResult(install, 1);

                    unregisterReceiver(this);
                    finish();
                }
            };

            //register receiver for when .apk download and install is complete
            registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        } else {
            downloadAndInstall(url,uri,destination);
        }

    }

    private void downloadAndInstall(String url,Uri duri,String destination) {
        final DownloadManager dManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        //request.setDestinationInExternalPublicDir("zmtmt", "zmtmt_zhibohao_update");
        request.setDestinationUri(duri);
        request.setDescription("Downloading");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.allowScanningByMediaScanner();
        }
        //request.setMimeType("application/vnd.android.package-archive");
        request.setVisibleInDownloadsUi(true);
        final long reference = dManager.enqueue(request);
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        upgradeReceiver = new UpgradeBroadcastReceiver(dManager, reference, duri, url);
        registerReceiver(upgradeReceiver, filter);
    }

    class UpgradeBroadcastReceiver extends BroadcastReceiver {
        private DownloadManager dManager;
        private long reference;
        Uri uri;
        String url;

        public UpgradeBroadcastReceiver(DownloadManager dManager, long reference, Uri uri, String url) {
            this.dManager = dManager;
            this.reference = reference;
            this.uri = uri;
            this.url = url;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            long myDownloadID = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (reference == myDownloadID) {
                /*Intent install = new Intent(Intent.ACTION_VIEW);
                Uri downloadFileUri=null;
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    downloadFileUri = FileProvider.getUriForFile(context, "xyz.kapps.kapps.fileprovider", new File(url));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }else {
                    downloadFileUri= dManager.getUriForDownloadedFile(reference);
                }
                install.setDataAndType(downloadFileUri, dManager.getMimeTypeForDownloadedFile(reference));
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(install,1);*/

                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                install.setDataAndType(uri,
                        dManager.getMimeTypeForDownloadedFile(reference));
                startActivityForResult(install, 1);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        // check if the request code is same as what is passed
        if(requestCode==1)
        {
            String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
            String fileName = "tempkapps"+this.position+".apk";
            destination += fileName;
            File file = new File(destination);
            if (file.exists())
                file.delete();
        }
    }
}
