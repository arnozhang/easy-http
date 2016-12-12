/**
 * Android Jungle-Major-Http framework project.
 *
 * Copyright 2016 Arno Zhang <zyfgood12@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jungle.majorhttp.manager;

import android.annotation.SuppressLint;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.jungle.majorhttp.model.base.AbstractModel;
import com.jungle.majorhttp.model.binary.DownloadFileRequestModel;
import com.jungle.majorhttp.model.binary.UploadRequestModel;
import com.jungle.majorhttp.model.listener.ModelRequestListener;
import com.jungle.majorhttp.network.CommonError;
import com.jungle.majorhttp.request.base.BizBaseRequest;
import com.jungle.majorhttp.request.base.BizBaseResponse;
import com.jungle.majorhttp.request.base.BizRequestListener;
import com.jungle.majorhttp.request.base.ExtraHeadersFiller;
import com.jungle.majorhttp.request.binary.BizBinaryRequest;
import com.jungle.majorhttp.request.binary.BizBinaryResponse;
import com.jungle.majorhttp.request.download.BizDownloadFileRequest;
import com.jungle.majorhttp.request.download.BizDownloadFileResponse;
import com.jungle.majorhttp.request.download.BizDownloadRequest;
import com.jungle.majorhttp.request.download.BizDownloadResponse;
import com.jungle.majorhttp.request.queue.RequestQueueFactory;
import com.jungle.majorhttp.request.text.BizTextRequest;
import com.jungle.majorhttp.request.text.BizTextResponse;
import com.jungle.majorhttp.request.upload.BizMultipartRequest;
import com.jungle.majorhttp.request.upload.BizMultipartResponse;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MajorHttpClient {

    public static final int DEFAULT_TIMEOUT_MS = 20 * 1000;
    public static final int UPLOAD_TIMEOUT_MS = 40 * 1000;


    private static MajorHttpClient mDefaultInstance;

    public static MajorHttpClient getDefault() {
        if (mDefaultInstance == null) {
            mDefaultInstance = new MajorHttpClient();
        }

        return mDefaultInstance;
    }


    private static class RequestNode {
        int mSeqId;
        Type mResponseType;
        Request<?> mVolleyRequest;
        ModelRequestListener mListener;

        public RequestNode(
                int seqId, Request<?> request,
                Type responseType, ModelRequestListener listener) {

            mSeqId = seqId;
            mVolleyRequest = request;
            mResponseType = responseType;
            mListener = listener;
        }

        public RequestNode(int seqId, Request<?> request, ModelRequestListener listener) {
            this(seqId, request, null, listener);
        }
    }


    @SuppressLint("UseSparseArrays")
    private Map<Integer, RequestNode> mRequestList = new HashMap<>();
    private AtomicInteger mSeqIdGenerator = new AtomicInteger();
    private RetryPolicy mDefaultRetryPolicy;
    private RetryPolicy mUploadRetryPolicy;
    private RequestQueue mRequestQueue;
    private ExtraHeadersFiller mExtraHeadersFiller;


    public MajorHttpClient() {
        this(null);
    }

    public MajorHttpClient(RequestQueueFactory factory) {
        this(DEFAULT_TIMEOUT_MS, UPLOAD_TIMEOUT_MS, factory);
    }

    public MajorHttpClient(int defaultTimeoutMs, int uploadTimeoutMs, RequestQueueFactory factory) {
        setUploadTimeoutMilliseconds(uploadTimeoutMs);
        setDefaultTimeoutMilliseconds(defaultTimeoutMs);
        setRequestQueueFactory(factory);
    }

    public void onTerminate() {
        mRequestList.clear();
    }

    public void setRequestQueueFactory(RequestQueueFactory factory) {
        if (factory != null) {
            mRequestQueue = factory.createRequestQueue();
        }
    }

    public void setUploadTimeoutMilliseconds(int milliseconds) {
        mUploadRetryPolicy = new DefaultRetryPolicy(milliseconds, 1, 1.0f);
    }

    public void setDefaultTimeoutMilliseconds(int milliseconds) {
        mDefaultRetryPolicy = new DefaultRetryPolicy(milliseconds, 1, 1.0f);
    }

    public void setExtraHeadersFiller(ExtraHeadersFiller filler) {
        mExtraHeadersFiller = filler;
    }

    public synchronized int loadTextModel(
            AbstractModel.Request request, ModelRequestListener<String> listener) {

        int seqId = nextSeqId();
        request.seqId(seqId);
        BizTextRequest jsonRequest = new BizTextRequest(
                seqId, request.getRequestMethod().toVolleyMethod(),
                request.getUrl(), request.getRequestParams(),
                request.getRequestHeaders(), request.getBody(),
                mBizTextRequestListener);

        addRequestNode(seqId, request, jsonRequest, listener);
        return seqId;
    }

    public synchronized int loadBinaryModel(
            AbstractModel.Request request, ModelRequestListener<byte[]> listener) {

        int seqId = nextSeqId();
        request.seqId(seqId);
        BizBinaryRequest binaryRequest = new BizBinaryRequest(
                seqId, request.getUrl(), request.getRequestParams(),
                request.getRequestHeaders(), request.getBody(),
                mBizBinaryRequestListener);

        addRequestNode(seqId, request, binaryRequest, listener);
        return seqId;
    }

    public synchronized int loadUploadModel(
            UploadRequestModel.Request request, ModelRequestListener<String> listener) {

        int seqId = nextSeqId();
        request.seqId(seqId);
        BizMultipartRequest uploadRequest = new BizMultipartRequest(
                seqId, request.getUrl(), request.getFormItems(),
                request.getRequestHeaders(),
                mBizUploadRequestListener);

        addRequestNode(seqId, request, uploadRequest, listener);
        return seqId;
    }

    public synchronized int loadDownloadModel(
            AbstractModel.Request request, ModelRequestListener<byte[]> listener) {

        int seqId = nextSeqId();
        request.seqId(seqId);
        BizDownloadRequest downloadRequest = new BizDownloadRequest(
                seqId, request.getUrl(), request.getRequestParams(),
                request.getRequestHeaders(),
                mBizDownloadRequestListener);

        addRequestNode(seqId, request, downloadRequest, listener);
        return seqId;
    }

    public synchronized int loadDownloadFileModel(
            DownloadFileRequestModel.Request request, ModelRequestListener<String> listener) {

        int seqId = nextSeqId();
        request.seqId(seqId);
        BizDownloadFileRequest downloadFileRequest = new BizDownloadFileRequest(
                seqId, request.getUrl(), request.getRequestParams(),
                request.getRequestHeaders(), request.getFilePath(),
                mBizDownloadFileRequestListener);

        addRequestNode(seqId, request, downloadFileRequest, listener);
        return seqId;
    }

    public synchronized int sendRequest(Request<?> request) {
        int seqId = nextSeqId();
        addRequestNode(seqId, null, request, null);
        return seqId;
    }

    private void addRequestNode(
            int seqId,
            AbstractModel.Request modelRequest,
            Request<?> request,
            ModelRequestListener listener) {

        if (mRequestQueue == null) {
            if (listener != null) {
                String message = "RequestQueue is null!"
                        + "use **setRequestQueueFactory** to initialize RequestQueue first!";
                listener.onError(seqId, CommonError.REQUEST_QUEUE_NOT_INITIALIZED, message);
            }

            return;
        }

        if (request instanceof BizMultipartRequest) {
            request.setRetryPolicy(mUploadRetryPolicy);
        } else {
            request.setRetryPolicy(mDefaultRetryPolicy);
        }

        if (request instanceof BizBaseRequest) {
            BizBaseRequest bizRequest = (BizBaseRequest) request;
            if (modelRequest.isFillExtraHeader()) {
                bizRequest.setExtraHeadersFiller(mExtraHeadersFiller);
            }
        }

        if (listener != null) {
            mRequestList.put(seqId, new RequestNode(seqId, request, listener));
        }

        mRequestQueue.add(request);
    }

    public synchronized void cancelBizModel(int seqId) {
        RequestNode node = mRequestList.remove(seqId);
        if (node != null) {
            node.mVolleyRequest.cancel();
        }
    }

    public RequestQueue getRequestQueue() {
        return mRequestQueue;
    }

    private int nextSeqId() {
        return mSeqIdGenerator.addAndGet(1);
    }

    private WrappedRequestListener<BizTextResponse> mBizTextRequestListener =
            new WrappedRequestListener<BizTextResponse>() {
                @Override
                protected void handleSuccess(int seqId,
                        ModelRequestListener<BizTextResponse> listener,
                        BizBaseResponse<BizTextResponse> response) {

                    if (response == null) {
                        listener.onSuccess(seqId, null, null);
                        return;
                    }

                    listener.onSuccess(seqId, response.mNetworkResp, response.mContent);
                }
            };

    private WrappedRequestListener<BizBinaryResponse> mBizBinaryRequestListener =
            new WrappedRequestListener<BizBinaryResponse>() {
                @Override
                protected void handleSuccess(int seqId,
                        ModelRequestListener<BizBinaryResponse> listener,
                        BizBaseResponse<BizBinaryResponse> response) {

                    if (response == null) {
                        listener.onSuccess(seqId, null, null);
                        return;
                    }

                    listener.onSuccess(seqId, response.mNetworkResp, response.mContent);
                }
            };

    private WrappedRequestListener<BizMultipartResponse> mBizUploadRequestListener =
            new WrappedRequestListener<BizMultipartResponse>() {
                @Override
                protected void handleSuccess(int seqId,
                        ModelRequestListener<BizMultipartResponse> listener,
                        BizBaseResponse<BizMultipartResponse> response) {

                    if (response == null) {
                        listener.onSuccess(seqId, null, null);
                        return;
                    }

                    listener.onSuccess(seqId, response.mNetworkResp, response.mContent);
                }
            };

    private WrappedRequestListener<BizDownloadResponse> mBizDownloadRequestListener =
            new WrappedRequestListener<BizDownloadResponse>() {
                @Override
                protected void handleSuccess(int seqId,
                        ModelRequestListener<BizDownloadResponse> listener,
                        BizBaseResponse<BizDownloadResponse> response) {

                    if (response == null) {
                        listener.onSuccess(seqId, null, null);
                        return;
                    }

                    listener.onSuccess(seqId, response.mNetworkResp, response.mContent);
                }
            };

    private WrappedRequestListener<BizDownloadFileResponse> mBizDownloadFileRequestListener =
            new WrappedRequestListener<BizDownloadFileResponse>() {
                @Override
                protected void handleSuccess(int seqId,
                        ModelRequestListener<BizDownloadFileResponse> listener,
                        BizBaseResponse<BizDownloadFileResponse> response) {

                    if (response == null) {
                        listener.onSuccess(seqId, null, null);
                        return;
                    }

                    listener.onSuccess(seqId, response.mNetworkResp, response.mContent);
                }
            };


    private abstract class WrappedRequestListener<T> implements BizRequestListener<T> {

        protected abstract void handleSuccess(
                int seqId, ModelRequestListener<T> listener, BizBaseResponse<T> response);


        @SuppressWarnings("unchecked")
        @Override
        public void onSuccess(int seqId, BizBaseResponse<T> response) {
            synchronized (MajorHttpClient.this) {
                RequestNode node = mRequestList.remove(seqId);
                if (node == null || node.mListener == null) {
                    return;
                }

                ModelRequestListener<T> listener = (ModelRequestListener<T>) node.mListener;
                handleSuccess(seqId, listener, response);
            }
        }

        @Override
        public void onError(int seqId, VolleyError error) {
            handleError(seqId, error);
        }
    }

    private synchronized void handleError(int seqId, VolleyError error) {
        RequestNode node = mRequestList.remove(seqId);
        if (node == null || node.mListener == null) {
            return;
        }

        node.mListener.onError(seqId, CommonError.fromError(error), error.toString());
    }
}
