package com.riskfreeroutes.app.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

public class CommunityReportItem implements ClusterItem {
    private final LatLng position;
    private final String title;
    private final String snippet;
    private final CommunityReport report;

    public CommunityReportItem(CommunityReport report) {
        this.report = report;
        this.position = new LatLng(report.getLocation().getLatitude(), report.getLocation().getLongitude());
        this.title = report.getType();
        this.snippet = report.getDescription();
    }

    @NonNull
    @Override
    public LatLng getPosition() {
        return position;
    }

    @Nullable
    @Override
    public String getTitle() {
        return title;
    }

    @Nullable
    @Override
    public String getSnippet() {
        return snippet;
    }

    @Nullable
    @Override
    public Float getZIndex() {
        return null;
    }

    public CommunityReport getReport() {
        return report;
    }
}
