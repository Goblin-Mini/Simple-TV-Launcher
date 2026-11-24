package com.simpletvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads preview channels published by installed TV apps. Visibility is launcher-local. */
public final class TvChannels {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final String PREFS = "tv_channels";
    private static final String SELECTED = "selected_channel_keys";
    private static final String ORDER = "channel_order_v1";

    public static final class Channel {
        final long id;
        final String packageName, internalId, name, appName;
        final String key;
        final List<Program> programs = new ArrayList<>();

        Channel(long id, String packageName, String internalId, String name, String appName) {
            this.id = id;
            this.packageName = packageName == null ? "" : packageName;
            this.internalId = internalId == null ? "" : internalId;
            this.name = name == null || name.trim().isEmpty() ? "Без названия" : name.trim();
            this.appName = appName == null || appName.trim().isEmpty() ? this.packageName : appName;
            this.key = this.packageName + "|" + this.internalId + "|" + this.name;
        }
    }

    public static final class Program {
        final String title, description, intentUri, posterUri;
        Program(String title, String description, String intentUri, String posterUri) {
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.intentUri = intentUri == null ? "" : intentUri;
            this.posterUri = posterUri == null ? "" : posterUri;
        }
    }

    static boolean hasPermission(Context c) {
        return Build.VERSION.SDK_INT < 23 ||
                c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** By default returns only channel metadata; program queries are always explicit and bounded. */
    static List<Channel> load(Context context, boolean selectedOnly) {
        return load(context, selectedOnly, 0);
    }

    /** Loads channel metadata and only fetches programs for the first N visible channels. */
    static List<Channel> load(Context context, boolean selectedOnly, int programChannelLimit) {
        List<Channel> result = new ArrayList<>();
        int programChannelsLoaded = 0;
        if (Build.VERSION.SDK_INT < 26 || !hasPermission(context)) return result;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        java.util.Set<String> selected = prefs.getStringSet(SELECTED, Collections.<String>emptySet());
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(
                TvContract.Channels.CONTENT_URI, projection, null, null,
                TvContract.Channels.COLUMN_DISPLAY_NAME + " ASC")) {
            if (cursor == null) return result;
            int idCol = cursor.getColumnIndexOrThrow(TvContract.Channels._ID);
            int pkgCol = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_PACKAGE_NAME);
            int internalCol = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID);
            int nameCol = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NAME);
            int typeCol = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_TYPE);
            while (cursor.moveToNext()) {
                if (!TvContract.Channels.TYPE_PREVIEW.equals(cursor.getString(typeCol))) continue;
                long id = cursor.getLong(idCol);
                String pkg = cursor.getString(pkgCol);
                String internal = cursor.getString(internalCol);
                String name = cursor.getString(nameCol);
                String app = pkg;
                try {
                    app = context.getPackageManager().getApplicationLabel(
                            context.getPackageManager().getApplicationInfo(pkg, 0)).toString();
                } catch (Exception ignored) { }
                Channel channel = new Channel(id, pkg, internal, name, app);
                if (!selectedOnly || selected.contains(channel.key)) {
                    if (programChannelsLoaded < programChannelLimit) {
                        readPrograms(context, channel, 12);
                        programChannelsLoaded++;
                    }
                    result.add(channel);
                }
            }
        } catch (SecurityException ignored) {
            // OEM firmware may not grant READ_TV_LISTINGS even after runtime approval.
        } catch (Exception ignored) { }
        List<String> order = order(context);
        if (!order.isEmpty()) {
            java.util.Map<String, Integer> positions = new java.util.HashMap<>();
            for (int i = 0; i < order.size(); i++) positions.put(order.get(i), i);
            Collections.sort(result, (a, b) -> Integer.compare(
                    positions.containsKey(a.key) ? positions.get(a.key) : Integer.MAX_VALUE,
                    positions.containsKey(b.key) ? positions.get(b.key) : Integer.MAX_VALUE));
        }
        return result;
    }

    static List<String> order(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ORDER, "");
        List<String> result = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) Collections.addAll(result, raw.split("\\n"));
        return result;
    }

    static void setOrder(Context context, List<Channel> channels) {
        StringBuilder value = new StringBuilder();
        for (Channel channel : channels) {
            if (value.length() > 0) value.append("\n");
            value.append(channel.key);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ORDER, value.toString()).apply();
    }

    static void loadPrograms(Context context, Channel channel, int limit) {
        if (channel == null) return;
        channel.programs.clear();
        readPrograms(context, channel, Math.max(1, Math.min(40, limit)));
    }

    private static void readPrograms(Context context, Channel channel, int limit) {
        String[] projection = {
                TvContract.PreviewPrograms._ID,
                TvContract.PreviewPrograms.COLUMN_TITLE,
                TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI
        };
        Uri uri = TvContract.buildPreviewProgramsUriForChannel(channel.id);
        List<Program> withoutArtwork = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
                TvContract.PreviewPrograms.COLUMN_WEIGHT + " DESC")) {
            if (cursor == null) return;
            int titleCol = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_TITLE);
            int descCol = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION);
            int intentCol = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_INTENT_URI);
            int posterCol = cursor.getColumnIndex(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI);
            while (cursor.moveToNext()) {
                Program program = new Program(cursor.getString(titleCol), cursor.getString(descCol),
                        cursor.getString(intentCol), cursor.getString(posterCol));
                if (!program.posterUri.isEmpty()) {
                    if (channel.programs.size() < limit) channel.programs.add(program);
                } else if (withoutArtwork.size() < limit) {
                    withoutArtwork.add(program);
                }
            }
            // Fill remaining places only after every available poster-bearing programme
            // has been considered. UI uses the publisher icon as a non-empty fallback.
            for (Program program : withoutArtwork) {
                if (channel.programs.size() >= limit) break;
                channel.programs.add(program);
            }
        } catch (Exception ignored) { }
    }

    /** True only when the user explicitly added this channel to the launcher home screen. */
    static boolean isSelected(Context context, Channel channel) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(SELECTED, Collections.<String>emptySet()).contains(channel.key);
    }

    static void setSelected(Context context, Channel channel, boolean selected) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        java.util.Set<String> values = new java.util.HashSet<>(prefs.getStringSet(SELECTED, Collections.<String>emptySet()));
        if (selected) values.add(channel.key); else values.remove(channel.key);
        prefs.edit().putStringSet(SELECTED, values).apply();
    }

    static int selectedCount(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(SELECTED, Collections.<String>emptySet()).size();
    }

    private TvChannels() { }
}
