package com.sample;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.link.preview.LinkPreview;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends RxAppCompatActivity {

    private ViewHolder viewHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        viewHolder = new ViewHolder(findViewById(android.R.id.content));

        viewHolder.buttonclear.setOnClickListener(view -> {
            viewHolder.resultUrl.setText(null);
            viewHolder.resultTitle.setText(null);
            viewHolder.resultImage.setImageBitmap(null);
        });

        viewHolder.buttonLoad.setOnClickListener(view -> {
            String link = viewHolder.editTextUrl.getText().toString();

            LinkPreview.preview(link)
                    .compose(bindToLifecycle())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            preview -> {
                                viewHolder.resultUrl.setText(preview.getUrl());
                                viewHolder.resultTitle.setText(preview.getTitle());
                                Glide.with(MainActivity.this).load(preview.getImageUrl()).into(viewHolder.resultImage);
                            },
                            throwable -> {
                                viewHolder.buttonclear.performClick();
                                Toast.makeText(MainActivity.this, R.string.error_url_loading, Toast.LENGTH_SHORT).show();
                            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewHolder = null;
    }

    private static class ViewHolder {

        public EditText editTextUrl;
        public TextView resultUrl;
        public TextView resultTitle;
        public ImageView resultImage;

        public Button buttonLoad;
        public Button buttonclear;

        public ViewHolder(View itemView) {
            editTextUrl = (EditText) itemView.findViewById(R.id.url);
            resultUrl = (TextView) itemView.findViewById(R.id.canonical_url);
            resultTitle = (TextView) itemView.findViewById(R.id.title);
            resultImage = (ImageView) itemView.findViewById(R.id.image);

            buttonLoad = (Button) itemView.findViewById(R.id.button_load);
            buttonclear = (Button) itemView.findViewById(R.id.button_clear);
        }

    }
}
