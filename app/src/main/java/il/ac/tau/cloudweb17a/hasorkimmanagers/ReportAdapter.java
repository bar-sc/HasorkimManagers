package il.ac.tau.cloudweb17a.hasorkimmanagers;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static il.ac.tau.cloudweb17a.hasorkimmanagers.Report.setLastReportStartTime;
import static il.ac.tau.cloudweb17a.hasorkimmanagers.User.getUser;


public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {
    private final Context context;
    private final Activity activity;
    private ArrayList<Report> mDataset = new ArrayList<>();
    final String TAG = "ReportAdapter";
    private boolean isOnlyOpen;
    private boolean isManager;


    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder

    public static class ReportViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public final TextView StatusView;
        public final TextView AddressView;
        public final TextView timeView;
        public final TextView numberScanners;
        public final TextView numberScannersTitle;
        public final TextView distance;
        private final TextView distanceReportTitle;
        final String TAG = ReportViewHolder.class.getSimpleName();
        private final TextView StatusViewTitle;
        private Report mReport;
        private Context context;


        public ReportViewHolder(View v) {
            super(v);
            v.setOnClickListener(this);
            StatusView = v.findViewById(R.id.report_status);
            StatusViewTitle = v.findViewById(R.id.report_status_title);
            AddressView = v.findViewById(R.id.report_address);
            timeView = v.findViewById(R.id.report_time);
            numberScanners = v.findViewById(R.id.numberScanners);
            numberScannersTitle = v.findViewById(R.id.numberScannersTitle);
            distance = v.findViewById(R.id.distanceReport);
            distanceReportTitle = v.findViewById(R.id.distanceReportTitle);
            context = v.getContext();
        }

        @Override
        public void onClick(View v) {

            Intent intent;

            if (getUser().getIsManager())
                intent = new Intent(context, ReportViewManagerActivity.class);
            else
                intent = new Intent(context, ReportViewScannerActivity.class);

            intent.putExtra("Report", mReport);
            context.startActivity(intent);
        }

        public void bindReport(Report report) {
            mReport = report;
            StatusView.setText(report.statusInHebrew(getUser()));
            AddressView.setText(report.getAddress());
            timeView.setText(report.getStartTimeAsString());
            if (!getUser().getIsManager()) {
                distance.setVisibility(View.VISIBLE);
                distanceReportTitle.setVisibility(View.VISIBLE);
                distance.setText(report.getDistance());

            } else {
                numberScanners.setVisibility(View.VISIBLE);
                numberScannersTitle.setVisibility(View.VISIBLE);
                StatusView.setVisibility(View.VISIBLE);
                StatusViewTitle.setVisibility(View.VISIBLE);
            }

            numberScanners.setText(Integer.toString(report.getAvailableScanners()));
        }

    }


    class SortbyDistance implements Comparator<Report> {
        public int compare(Report a, Report b) {
            return a.getDistancevalue() - b.getDistancevalue();
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public ReportAdapter(boolean isOnlyOpenP, boolean isManagerP, Context contextP, Activity activityP, final ReportListActivity.MyCallBackClass setUIVisible, int numberOfReports) {
        this.isOnlyOpen = isOnlyOpenP;
        this.isManager = isManagerP;
        this.context = contextP;
        this.activity = activityP;

        DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference().child("reports");
        Query query;
        if (isManager) {
            query = reportsRef.orderByChild("startTime").limitToFirst(numberOfReports);
        } else {
            query = reportsRef.orderByChild("status").equalTo("NEW");
        }
        Log.d(TAG, "only open:" + isOnlyOpen + ", is manager:" + isManager);

        updateList(query, setUIVisible);


    }

    public void updateList(Query query, final ReportListActivity.MyCallBackClass setUIVisible) {
        Log.d(TAG, "updating list");
        query.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                try {
                    final Report report = dataSnapshot.getValue(Report.class);
                    String key = dataSnapshot.getKey();
                    report.setId(key);

                    if (isManager) {
                        if (!isOnlyOpen) {
                            mDataset.add(report);
                            setLastReportStartTime(report.getStartTime());
                        } else {
                            if (report.isOpenReport()) {
                                mDataset.add(report);
                                setLastReportStartTime(report.getStartTime());
                            }
                        }
                    } else {

                        ArrayList<LatLong> locationList = new ArrayList<>();
                        LatLong thisLatLong = new LatLong();
                        thisLatLong.Lat = report.getLat();
                        thisLatLong.Long = report.getLong();
                        locationList.add(thisLatLong);


                        Log.d(TAG, "sendRequest");
                        distanceService.getDistanceRequest(locationList, context.getString(R.string.google_maps_key)).enqueue(
                                new Callback() {

                                    Runnable updateUI = new Runnable() {
                                        @Override
                                        public void run() {
                                            //TextView dText = activity.findViewById(R.id.dText);
                                            //dText.setText(String.valueOf("gotten to callback: "+ distance+""+report.getId()));
                                            if (mDataset.size() == 0) {
                                                setUIVisible.execute();
                                            }
                                            mDataset.add(report);
                                            Collections.sort(mDataset, new SortbyDistance());

                                            notifyDataSetChanged();
                                        }
                                    };

                                    @Override
                                    public void onFailure(Call call, IOException e) {
                                        Log.e(TAG, "no response from distances");
                                        //probably should retry

                                    }

                                    @Override
                                    public void onResponse(Call call, Response response) throws IOException {
                                        ResponseBody responseBody = response.body();
                                        String[] parsedDate = distanceService.parseJSON(responseBody.string());
                                        if (parsedDate == null) {
                                            Log.d(TAG, "couldn't get distance");
                                            //TODO decide what to do here
                                            report.setDistance("");
                                            report.setDuration("");
                                            report.setDistancevalue(1000000000);
                                            activity.runOnUiThread(updateUI);

                                        } else {
                                            report.setDistance(parsedDate[0]);
                                            report.setDuration(parsedDate[1]);
                                            report.setDistancevalue(Integer.parseInt(parsedDate[2]));
                                            //update
                                            //distance.setText(distance);
                                            activity.runOnUiThread(updateUI);
                                        }
                                    }
                                }
                        );

                    }

                    //Log.d(TAG, "added a report");
                    //Collections.sort(mDataset, new SortbyId());
                    notifyDataSetChanged();
                } catch (Exception e) {
                    return;
                }

            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {

                //getting key of report to update
                Report report = dataSnapshot.getValue(Report.class);
                String key = dataSnapshot.getKey();
                report.setId(key);
                //looking for report to update
                int index = -1;
                for (int i = 0; i < mDataset.size(); i++) {
                    if (mDataset.get(i) != null && mDataset.get(i).getId() != null && mDataset.get(i).getId().equals(key)) {
                        index = i;
                    }
                }

                //updating
                if (index != -1) {
                    mDataset.set(index, report);
                    notifyDataSetChanged();
                } else {
                    Log.w(TAG, "Failed to find value in local report list");
                }

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
            }


            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });

    }

    // Create new views (invoked by the layout manager)
    @Override
    public ReportViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // create a new view
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.report_list_item, parent, false);

        // set the view's size, margins, paddings and layout parameters
        ReportViewHolder vh = new ReportViewHolder(v);
        return vh;

    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ReportViewHolder holder, int position) {
        holder.bindReport(mDataset.get(position));
    }


    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}