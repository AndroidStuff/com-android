package com.sidzi.circleofmusic.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.rollbar.android.Rollbar;
import com.sidzi.circleofmusic.BuildConfig;
import com.sidzi.circleofmusic.R;
import com.sidzi.circleofmusic.adapters.ChatAdapter;
import com.sidzi.circleofmusic.adapters.PotmAdapter;
import com.sidzi.circleofmusic.adapters.TracksAdapter;
import com.sidzi.circleofmusic.ai.Trebie;
import com.sidzi.circleofmusic.helpers.AudioEventHandler;
import com.sidzi.circleofmusic.helpers.BucketSaver;
import com.sidzi.circleofmusic.helpers.DatabaseSynchronization;
import com.sidzi.circleofmusic.helpers.LocalMusicLoader;
import com.sidzi.circleofmusic.helpers.VerticalSpaceDecorationHelper;

import net.gotev.uploadservice.UploadService;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    public static String com_url = "http://circleofmusic-sidzi.rhcloud.com/";
    public static String rollbar_key = "";
    public static String wit_ai_key = "";
    private AudioEventHandler mAudioEventHandler;
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        TODO remove key before commit
        Rollbar.init(this, rollbar_key, "production");
        setTheme(R.style.AppTheme_NoActionBar);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                String[] perms = {"android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"};
                requestPermissions(perms, 202);
            }
        } else {
            UploadService.NAMESPACE = BuildConfig.APPLICATION_ID;

            RequestQueue requestQueue = Volley.newRequestQueue(this);
            JsonObjectRequest eosCheck = new JsonObjectRequest(Request.Method.GET, com_url + "checkEOSVersion", null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        if ((int) response.get("eos_version") > BuildConfig.VERSION_CODE) {
                            startActivity(new Intent(MainActivity.this, EosActivity.class));
                            finish();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {

                }
            });
            requestQueue.add(eosCheck);
            mAudioEventHandler = new AudioEventHandler();
            registerReceiver(mAudioEventHandler, new IntentFilter("com.sidzi.circleofmusic.PLAY_TRACK"));
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            ComponentName componentName = new ComponentName(getPackageName(), AudioEventHandler.class.getName());
            audioManager.registerMediaButtonEventReceiver(componentName);
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            // Create the adapter that will return a fragment for each of the four
            // primary sections of the activity.
            mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

            // Set up the ViewPager with the sections adapter.
            mViewPager = (ViewPager) findViewById(R.id.container);
            mViewPager.setAdapter(mSectionsPagerAdapter);

            TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
            tabLayout.setupWithViewPager(mViewPager);
            final FrameLayout fl = (FrameLayout) findViewById(R.id.flPlayer);
            mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                }

                @Override
                public void onPageSelected(int position) {
                    if (position == 2) {
                        fl.setVisibility(View.GONE);
                    } else {
                        if (fl.getVisibility() != View.VISIBLE) {
                            fl.setVisibility(View.VISIBLE);
                        }
                        if (position == 1) {
                            mSectionsPagerAdapter.notifyDataSetChanged();
                        }
                    }
                }

                @Override
                public void onPageScrollStateChanged(int state) {

                }
            });
            SharedPreferences settings = getSharedPreferences("com_prefs", 0);
            if (settings.getBoolean("init", true)) {
                BucketSaver bucketSaver = new BucketSaver(this);
                if (bucketSaver.importFile())
                    settings.edit().putBoolean("init", false).apply();
            }
            new DatabaseSynchronization(MainActivity.this).execute();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        recreate();
    }

    @Override
    protected void onDestroy() {
        try {
            BucketSaver bucketSaver = new BucketSaver(this);
            bucketSaver.saveFile();
            if (AudioEventHandler.mMediaPlayer.isPlaying()) {
//                Add background service here
                AudioEventHandler.mMediaPlayer.stop();
            }
            AudioEventHandler.mMediaPlayer.reset();
            AudioEventHandler.mMediaPlayer.release();
            AudioEventHandler.mNotificationManager.cancelAll();
            unregisterReceiver(mAudioEventHandler);
        } catch (IllegalArgumentException | NullPointerException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {

        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            RecyclerView mRecyclerView;
            RecyclerView.LayoutManager mLayoutManager;
            View homeView = inflater.inflate(R.layout.fragment_track_list, container, false);
            mRecyclerView = (RecyclerView) homeView.findViewById(R.id.rVTrackList);
            mLayoutManager = new LinearLayoutManager(getContext());

            assert mRecyclerView != null;
            mRecyclerView.setLayoutManager(mLayoutManager);
            mRecyclerView.setHasFixedSize(true);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                mRecyclerView.addItemDecoration(new VerticalSpaceDecorationHelper(getContext()));
            }

            switch (getArguments().getInt(ARG_SECTION_NUMBER)) {
                case 1:
                    TracksAdapter tracksAdapter1 = new TracksAdapter(getContext());
                    LocalMusicLoader lml = new LocalMusicLoader(getContext(), tracksAdapter1);
                    lml.execute();
                    mRecyclerView.setAdapter(tracksAdapter1);
                    break;
                case 2:
                    final PotmAdapter potmAdapter = new PotmAdapter(getContext());
                    mRecyclerView.setAdapter(potmAdapter);
                    break;
                case 3:
                    homeView = inflater.inflate(R.layout.fragment_chat_bot, container, false);
                    final RecyclerView chatRecyclerView = (RecyclerView) homeView.findViewById(R.id.rvChatConsole);
                    final Trebie mTrebie = new Trebie(getContext());
                    final ChatAdapter chatAdapter = new ChatAdapter();
                    final LinearLayoutManager chatLayoutManager = new LinearLayoutManager(getContext());
                    mTrebie.setmChatAdapter(chatAdapter);
                    mTrebie.setmRecyclerView(chatRecyclerView);
                    chatLayoutManager.setStackFromEnd(true);
                    chatRecyclerView.setAdapter(chatAdapter);
                    chatRecyclerView.setLayoutManager(chatLayoutManager);
                    ImageButton ibSend = (ImageButton) homeView.findViewById(R.id.ibSendMessage);
                    final EditText etChatMessage = (EditText) homeView.findViewById(R.id.etChatMessage);
                    etChatMessage.setHint("Say \"help me\" to Trebie to get started");
                    ibSend.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            etChatMessage.setHint("");
                            String message = etChatMessage.getText().toString();
                            if (!message.equals("")) {
                                chatAdapter.addMessage(message, true);
                                chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount());
                                etChatMessage.setText("");
                                mTrebie.converse(message, null);
                            }
                        }
                    });
                    break;
                case 4:
                    TracksAdapter tracksAdapter2 = new TracksAdapter(getContext());
                    tracksAdapter2.getBucketedTracks();
                    mRecyclerView.setAdapter(tracksAdapter2);
                    break;
            }
            return homeView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 4 total pages.
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Local";
                case 1:
                    return "POTM";
                case 2:
                    return "Trebie";
                case 3:
                    return "Bucket";
            }
            return null;
        }
    }
}