package com.curtismj.logoplus;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.curtismj.logoplus.persist.RingColor;

import java.util.List;

public class RingColorAdapter extends ArrayAdapter<RingColor> {

    public List<RingColor> colList;
    LayoutInflater layoutInflater;

    public RingColorAdapter(Context context, int textViewResourceId,
                              List<RingColor> colList) {
        super(context, textViewResourceId, colList);
        this.colList = colList;
        layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return ((null != colList) ? colList.size() : 0);
    }

    @Override
    public RingColor getItem(int position) {
        return ((null != colList) ? colList.get(position) : null);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (null == view) {
            view = layoutInflater.inflate(R.layout.ring_num_row, null);
        }

        RingColor ringCol = colList.get(position);
        if (null != ringCol) {
            TextView friendlyName = (TextView) view.findViewById(R.id.num_name);
            TextView number = (TextView) view.findViewById(R.id.num_number);
            View rect = view.findViewById(R.id.ring_color_rect);
            rect.setBackgroundColor(ringCol.color);

            friendlyName.setText(ringCol.friendlyName);
            number.setText(ringCol.number);
        }

        return view;
    }
}
