package com.curtismj.logoplus;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import com.rarepebble.colorpicker.ColorObserver;
import com.rarepebble.colorpicker.ColorPickerView;
import com.rarepebble.colorpicker.ObservableColor;

public class CommonUtils {
    public interface ColorPickCallback
    {
        void run(int color);
    }

    public interface BlankCallback
    {
        void run();
    }

    public static void colorPickDialog(Context context, int initial, final ColorPickCallback ok, final ColorPickCallback remove, final ColorObserver observer, final  BlankCallback cancel) {
        final ColorPickerView picker = new ColorPickerView(context);
        picker.setColor(initial);
        picker.showAlpha(false);
        picker.showHex(true);
        picker.showPreview(true);
        if (observer != null)
            picker.addColorObserver(observer);

        AlertDialog.Builder pickerBuilder = new AlertDialog.Builder(context);
        pickerBuilder
                .setTitle(null)
                .setView(picker)
                .setPositiveButton(R.string.ok_text, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ok.run(picker.getColor());
                    }
                })
                .setNegativeButton(R.string.cancel, null);

        if (cancel != null) {
            pickerBuilder
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            cancel.run();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            cancel.run();
                        }
                    });
        }

        if (remove != null) {
            pickerBuilder.setNeutralButton(R.string.remove_effect, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    remove.run(picker.getColor());
                }
            });
        }

        AlertDialog pickerDialog = pickerBuilder.create();
        pickerDialog.show();
    }

    public static void genericDialog(Context context, int titleR, int messageR)
    {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(titleR);
        dialogBuilder.setMessage(messageR);
        dialogBuilder.setNeutralButton(R.string.ok_text, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog dialog = dialogBuilder.create();
        dialog.show();
    }
}
