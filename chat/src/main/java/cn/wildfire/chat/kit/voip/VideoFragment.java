package cn.wildfire.chat.kit.voip;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import org.webrtc.Logging;
import org.webrtc.RendererCommon;
import org.webrtc.StatsReport;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.wildfire.chat.kit.WfcUIKit;
import cn.wildfire.chat.kit.user.UserViewModel;
import cn.wildfirechat.avenginekit.AVEngineKit;
import cn.wildfirechat.chat.R;
import cn.wildfirechat.model.UserInfo;

public class VideoFragment extends Fragment implements AVEngineKit.CallSessionCallback {

    @Bind(R.id.pip_video_view)
    FrameLayout pipRenderer;
    @Bind(R.id.fullscreen_video_view)
    FrameLayout fullscreenRenderer;
    @Bind(R.id.minimizeImageView)
    ImageView minimizeImageView;
    @Bind(R.id.outgoingActionContainer)
    ViewGroup outgoingActionContainer;
    @Bind(R.id.incomingActionContainer)
    ViewGroup incomingActionContainer;
    @Bind(R.id.connectedActionContainer)
    ViewGroup connectedActionContainer;
    @Bind(R.id.inviteeInfoContainer)
    ViewGroup inviteeInfoContainer;
    @Bind(R.id.portraitImageView)
    ImageView portraitImageView;
    @Bind(R.id.nameTextView)
    TextView nameTextView;
    @Bind(R.id.descTextView)
    TextView descTextView;
    @Bind(R.id.durationTextView)
    TextView durationTextView;

    SurfaceView localSurfaceView;
    SurfaceView remoteSurfaceView;

    // True if local view is in the fullscreen renderer.
    private boolean isSwappedFeeds;
    private boolean isOutgoing;
    private String targetId;
    private AVEngineKit gEngineKit;

    private RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_BALANCED;

    private boolean callControlVisible = true;

    private Toast logToast;
    private static final String TAG = "VideoFragment";

    public static VideoFragment newInstance(String targetId, boolean isOutgoing) {

        VideoFragment fragment = new VideoFragment();
        Bundle args = new Bundle();
        args.putBoolean("outgoing", isOutgoing);
        args.putString("targetId", targetId);
        fragment.setArguments(args);
        return fragment;
    }

    private AsyncTask<Void,Void,Void> answerTask;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isOutgoing = ((SingleVoipCallActivity) getActivity()).isOutgoing();
        targetId = ((SingleVoipCallActivity) getActivity()).getTargetId();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.av_p2p_video_layout, container, false);
        ButterKnife.bind(this, view);
        init();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void didCallEndWithReason(AVEngineKit.CallEndReason reason) {
        // never called
    }

    @Override
    public void didChangeState(AVEngineKit.CallState state) {
        if (state == AVEngineKit.CallState.Connected) {
            incomingActionContainer.setVisibility(View.GONE);
            outgoingActionContainer.setVisibility(View.GONE);
            connectedActionContainer.setVisibility(View.VISIBLE);
            inviteeInfoContainer.setVisibility(View.GONE);
            minimizeImageView.setVisibility(View.VISIBLE);
        } else {
            // do nothing now
        }
    }

    @Override
    public void didChangeMode(boolean audioOnly) {
        if (audioOnly) {
            gEngineKit.getCurrentSession().setupLocalVideo(null, scalingType);
            gEngineKit.getCurrentSession().setupRemoteVideo(null, scalingType);
        }
    }

    @Override
    public void didCreateLocalVideoTrack() {
        SurfaceView surfaceView = gEngineKit.getCurrentSession().createRendererView();
        if (surfaceView != null) {
            surfaceView.setZOrderMediaOverlay(true);
            localSurfaceView = surfaceView;
            if (isOutgoing && remoteSurfaceView == null) {
                fullscreenRenderer.addView(surfaceView);
            } else {
                pipRenderer.addView(surfaceView);
            }
            gEngineKit.getCurrentSession().setupLocalVideo(surfaceView, scalingType);
        }
    }

    @Override
    public void didReceiveRemoteVideoTrack() {
        pipRenderer.setVisibility(View.VISIBLE);
        if (isOutgoing && localSurfaceView != null) {
            ((ViewGroup) localSurfaceView.getParent()).removeView(localSurfaceView);
            pipRenderer.addView(localSurfaceView);
            gEngineKit.getCurrentSession().setupLocalVideo(localSurfaceView, scalingType);
        }

        SurfaceView surfaceView = gEngineKit.getCurrentSession().createRendererView();
        if (surfaceView != null) {
            remoteSurfaceView = surfaceView;
            fullscreenRenderer.removeAllViews();
            fullscreenRenderer.addView(surfaceView);
            gEngineKit.getCurrentSession().setupRemoteVideo(surfaceView, scalingType);
        }
    }

    @Override
    public void didError(String error) {

    }

    @Override
    public void didGetStats(StatsReport[] reports) {
        //hudFragment.updateEncoderStatistics(reports);
        // TODO
    }

    @Override
    public void onStart() {
        super.onStart();
        answerTask = new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                VideoFragment.this.accept();
            }
        };

        answerTask.execute();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (answerTask != null) {
            answerTask.cancel(true);
            answerTask = null;
        }
    }

    @OnClick(R.id.acceptImageView)
    public void accept() {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() == AVEngineKit.CallState.Incoming) {
            session.answerCall(false);
        }
    }

    @OnClick({R.id.incomingAudioOnlyImageView})
    public void audioAccept() {
        ((SingleVoipCallActivity) getActivity()).audioAccept();
    }

    @OnClick({R.id.outgoingAudioOnlyImageView, R.id.connectedAudioOnlyImageView})
    public void audioCall() {
        ((SingleVoipCallActivity) getActivity()).audioCall();
    }

    // callFragment.OnCallEvents interface implementation.
    @OnClick({R.id.connectedHangupImageView,
            R.id.outgoingHangupImageView,
            R.id.incomingHangupImageView})
    public void hangUp() {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null) {
            session.endCall();
        } else {
            getActivity().finish();
        }
    }

    @OnClick(R.id.switchCameraImageView)
    public void switchCamera() {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null) {
            session.switchCamera();
        }
    }

    public void onVideoScalingSwitch(RendererCommon.ScalingType scalingType) {
        this.scalingType = scalingType;

        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
            for (int i = 0; i < fullscreenRenderer.getChildCount(); i++) {
                View view = fullscreenRenderer.getChildAt(i);
                if (view instanceof SurfaceView) {
                    session.setVideoScalingType((SurfaceView) view, scalingType);
                    break;
                }
            }
        }
    }

    @OnClick(R.id.fullscreen_video_view)
    void toggleCallControlVisibility() {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session == null || session.getState() != AVEngineKit.CallState.Connected) {
            return;
        }
        callControlVisible = !callControlVisible;
        if (callControlVisible) {
            connectedActionContainer.setVisibility(View.VISIBLE);
        } else {
            connectedActionContainer.setVisibility(View.GONE);
        }
        // TODO animation
    }

    @OnClick(R.id.minimizeImageView)
    public void minimize() {
        gEngineKit.getCurrentSession().stopVideoSource();
        ((SingleVoipCallActivity) getActivity()).showFloatingView();
    }

    // Log |msg| and Toast about it.
    private void logAndToast(String msg) {
        Log.d(TAG, msg);
        if (logToast != null) {
            logToast.cancel();
        }
        logToast = Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT);
        logToast.show();
    }

    @OnClick(R.id.pip_video_view)
    void setSwappedFeeds() {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() == AVEngineKit.CallState.Connected) {
            Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
            this.isSwappedFeeds = !isSwappedFeeds;
            session.setupRemoteVideo(isSwappedFeeds ? localSurfaceView : remoteSurfaceView, scalingType);
            session.setupLocalVideo(isSwappedFeeds ? remoteSurfaceView : localSurfaceView, scalingType);
        }
    }

    private void init() {
        gEngineKit = ((SingleVoipCallActivity) getActivity()).getEngineKit();
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session == null || AVEngineKit.CallState.Idle == session.getState()) {
            getActivity().finish();
        } else if (AVEngineKit.CallState.Connected == session.getState()) {
            incomingActionContainer.setVisibility(View.GONE);
            outgoingActionContainer.setVisibility(View.GONE);
            connectedActionContainer.setVisibility(View.VISIBLE);
            inviteeInfoContainer.setVisibility(View.GONE);
            minimizeImageView.setVisibility(View.VISIBLE);

            session.startVideoSource();
            didCreateLocalVideoTrack();
            didReceiveRemoteVideoTrack();
        } else {
            if (isOutgoing) {
                incomingActionContainer.setVisibility(View.GONE);
                outgoingActionContainer.setVisibility(View.VISIBLE);
                connectedActionContainer.setVisibility(View.GONE);
                descTextView.setText(R.string.av_waiting);
            } else {
                incomingActionContainer.setVisibility(View.VISIBLE);
                outgoingActionContainer.setVisibility(View.GONE);
                connectedActionContainer.setVisibility(View.GONE);
                descTextView.setText(R.string.av_video_invite);
            }
        }
        UserViewModel userViewModel = WfcUIKit.getAppScopeViewModel(UserViewModel.class);
        UserInfo userInfo = userViewModel.getUserInfo(targetId, false);
        if (userInfo == null) {
            getActivity().finish();
            return;
        }
        Glide.with(this).load(userInfo.portrait).into(portraitImageView);
        nameTextView.setText(userViewModel.getUserDisplayName(userInfo));

        updateCallDuration();
    }

    private Handler handler = new Handler();

    private void updateCallDuration() {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() == AVEngineKit.CallState.Connected) {
            long s = System.currentTimeMillis() - session.getStartTime();
            s = s / 1000;
            String text;
            if (s > 3600) {
                text = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
            } else {
                text = String.format("%02d:%02d", s / 60, (s % 60));
            }
            durationTextView.setText(text);
        }
        handler.postDelayed(this::updateCallDuration, 1000);
    }
}
