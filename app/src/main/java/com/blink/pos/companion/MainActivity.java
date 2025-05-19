package com.blink.pos.companion;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.appcompat.app.AppCompatActivity;
import net.nyx.printerservice.print.IPrinterService;
import net.nyx.printerservice.print.PrintTextFormat;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private IPrinterService printerService;
    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private boolean isPrinterServiceBound = false;
    private String[] pendingPrintData = null;

    private ServiceConnection connService = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
            isPrinterServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            printerService = IPrinterService.Stub.asInterface(service);
            isPrinterServiceBound = true;
            if (pendingPrintData != null) {
                dispatchPrintRequest(pendingPrintData);
                pendingPrintData = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService();
        handleSendText(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSendText(intent);
    }

    private void handleSendText(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                String[] printData = extractPrintData(data);
                if (isPrinterServiceBound) {
                    dispatchPrintRequest(printData);
                } else {
                    pendingPrintData = printData;
                }
                finish();
            }
        }
    }

    private String[] extractPrintData(Uri data) {
        String app = data.getQueryParameter("app");
        if (app != null && app.equals("voucher")) {
            return extractVoucherData(data);
        }
        return extractPayData(data);
    }

    private String[] extractVoucherData(Uri data) {
        String lnurl = data.getQueryParameter("lnurl");
        String voucherPrice = data.getQueryParameter("voucherPrice");
        String voucherAmount = data.getQueryParameter("voucherAmount");
        String voucherSecret = data.getQueryParameter("voucherSecret");
        String commissionPercentage = data.getQueryParameter("commissionPercentage");
        String identifierCode = data.getQueryParameter("identifierCode");
        return new String[]{"voucher", lnurl, voucherPrice, voucherAmount, voucherSecret, commissionPercentage, identifierCode};
    }

    private String[] extractPayData(Uri data) {
        String ticket = data.getQueryParameter("text");
        if (ticket != null && !ticket.isEmpty()) {
            return new String[]{"raw", ticket};
        }
        String username      = data.getQueryParameter("username");
        String amount        = data.getQueryParameter("amount");
        String transactionId = data.getQueryParameter("id");
        String date          = data.getQueryParameter("date");
        String time          = data.getQueryParameter("time");
        return new String[]{"pay", username, amount, transactionId, date, time};
    }

    private void dispatchPrintRequest(String[] printData) {
        switch (printData[0]) {
            case "voucher":
                printVoucherReceipt(
                        printData[1], printData[2], printData[3],
                        printData[4], printData[5], printData[6]
                );
                break;
            case "raw":
                printRawReceipt(printData[1]);
                break;
            case "pay":
            default:
                printPayReceipt(
                        printData[1], printData[2],
                        printData[3], printData[4], printData[5]
                );
                break;
        }
    }

    private void printRawReceipt(String ticket) {
        singleThreadExecutor.submit(() -> {
            try {
                // Print Blink logo
                Bitmap logo = BitmapFactory.decodeStream(getAssets().open("hivedebit.png"));
                int maxW = 200;
                double ar = (double) logo.getWidth() / logo.getHeight();
                Bitmap resized = Bitmap.createScaledBitmap(logo, maxW, (int) (maxW / ar), true);
                printerService.printBitmap(resized, 1, 1);

                // Print raw ticket text
                PrintTextFormat fmt = new PrintTextFormat();
                fmt.setAli(1);
                fmt.setTextSize(25);
                fmt.setStyle(1);
                printerService.printText(ticket, fmt);
                paperOut();
            } catch (RemoteException | IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void printVoucherReceipt(
            String lnurl, String voucherPrice, String voucherAmount,
            String voucherSecret, String commissionPercentage, String identifierCode
    ) {
        singleThreadExecutor.submit(() -> {
            try {
                PrintTextFormat dashed = new PrintTextFormat();
                dashed.setStyle(1);
                dashed.setTextSize(27);
                dashed.setAli(1);
                Bitmap logo = BitmapFactory.decodeStream(getAssets().open("hivedebit.png"));
                int maxW = 200;
                double ar = (double) logo.getWidth() / logo.getHeight();
                Bitmap resized = Bitmap.createScaledBitmap(logo, maxW, (int)(maxW/ar), true);
                printerService.printBitmap(resized, 1, 1);
                String sep = new String(new char[32]).replace("\0","-");
                printerService.printText(sep, dashed);
                printDynamicKeyValue("Price:"," ", voucherPrice);
                printDynamicKeyValue("Value:"," ", voucherAmount);
                printDynamicKeyValue("Identifier:"," ", identifierCode);
                printDynamicKeyValue("Commission:"," ", commissionPercentage+"%");
                printerService.printText(sep, dashed);
                printerService.printQrCode(lnurl,350,350,1);
                printerService.printText(sep, dashed);
                PrintTextFormat linkFmt = new PrintTextFormat();
                linkFmt.setAli(1); linkFmt.setTextSize(25); linkFmt.setStyle(1);
                printerService.printText("voucher.blink.sv", linkFmt);
                printerService.printText("\n", linkFmt);
                paperOut();
            } catch (RemoteException | IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void printPayReceipt(
            String username, String amount,
            String transactionId, String date, String time
    ) {
        singleThreadExecutor.submit(() -> {
            try {
                PrintTextFormat dashed = new PrintTextFormat();
                dashed.setStyle(1);
                dashed.setTextSize(27);
                dashed.setAli(1);
                Bitmap logo = BitmapFactory.decodeStream(getAssets().open("hivedebit.png"));
                int maxW = 200;
                double ar = (double) logo.getWidth() / logo.getHeight();
                Bitmap resized = Bitmap.createScaledBitmap(logo, maxW, (int)(maxW/ar), true);
                printerService.printBitmap(resized, 1, 1);
                String sep = new String(new char[32]).replace("\0","-");
                printerService.printText(sep, dashed);
                printDynamicKeyValue("Username:"," ", username);
                printDynamicKeyValue("Amount:", " ", amount);
                if (date != null && !date.isEmpty()) {
                    printDynamicKeyValue("Date:"," ", date);
                }
                if (time != null && !time.isEmpty()) {
                    printDynamicKeyValue("Time:"," ", time);
                }
                printerService.printText(sep, dashed);
                if (transactionId != null && !transactionId.isEmpty()) {
                    PrintTextFormat txFmt = new PrintTextFormat();
                    txFmt.setAli(1); txFmt.setTextSize(23); txFmt.setStyle(1);
                    printerService.printText("Blink Internal Id", txFmt);
                    printerService.printText(transactionId, txFmt);
                    printerService.printText("\n", txFmt);
                }
                PrintTextFormat foot = new PrintTextFormat();
                foot.setAli(1); foot.setTextSize(23); foot.setStyle(1);
                printerService.printText("CONCORDE VERSION", foot);
                printerService.printText("Powered by Hive", foot);
                printerService.printText("\n", foot);
                paperOut();
            } catch (RemoteException | IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void printDynamicKeyValue(String key, String space, String value) throws RemoteException {
        PrintTextFormat fmt = new PrintTextFormat();
        fmt.setStyle(1);
        fmt.setTextSize(23);
        fmt.setAli(0);
        printerService.printText(key + space + value, fmt);
    }

    private void bindService() {
        Intent intent = new Intent();
        intent.setPackage("net.nyx.printerservice");
        intent.setAction("net.nyx.printerservice.IPrinterService");
        bindService(intent, connService, Context.BIND_AUTO_CREATE);
    }

    private void paperOut() {
        singleThreadExecutor.submit(() -> {
            try { printerService.paperOut(80); }
            catch (RemoteException e) { e.printStackTrace(); }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isPrinterServiceBound) {
            unbindService(connService);
            isPrinterServiceBound = false;
        }
    }
}
