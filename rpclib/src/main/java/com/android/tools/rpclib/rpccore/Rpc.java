/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.rpclib.rpccore;

import com.android.tools.rpclib.futures.FutureController;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.*;

/**
 * Holds static method helpers for getting RPC call results from {@link ListenableFuture}s
 * created from RPC calls.
 */
public class Rpc {
    private static final Executor EXECUTOR = MoreExecutors.sameThreadExecutor();

    /**
     * Blocks and waits for the result of the RPC call, or throws an exception if the RPC call was not
     * successful.
     *
     * <p>{@link RpcException}s packed in {@link ExecutionException}s thrown by the
     * {@link ListenableFuture} are unpacked and rethrown so they can be explicitly
     * handled using {@code catch} clauses by the caller.
     *
     * @param <V> the result value type.
     * @return the result value.
     * @throws RpcException          if there was an error raised by the server.
     * @throws ExecutionException    if there was a non-{@link RpcException} thrown by the
     *                               {@link ListenableFuture}.
     * @throws CancellationException if the computation was cancelled.
     */
    public static <V> V get(final ListenableFuture<V> future, long timeout, TimeUnit unit)
        throws RpcException, TimeoutException, ExecutionException {
        try {
            return Uninterruptibles.getUninterruptibly(future, timeout, unit);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (!(cause instanceof RpcException)) {
                throw e;
            }
            throw (RpcException)cause;
        }
    }

    /**
     * Calls {@link Callback#onFinish} with the {@link Result} once the {@link ListenableFuture} RPC call
     * has either successfully completed or thrown an exception.
     *
     * <p>If {@link Callback#onFinish} does not throw an uncaught,
     * non-{@link CancellationException} then it is logged as an error to the provided
     * {@link Logger}.
     *
     * @param future   the {@link ListenableFuture} returned by the invoking the RPC call.
     * @param log      the {@link Logger} used for logging uncaught exceptions thrown from {@link Callback#onFinish}.
     * @param callback the {@link Callback} to handle {@link Callback#onStart} and {@link Callback#onFinish} events.
     * @param <V>      the RPC result type.
     */
    public static <V> void listen(ListenableFuture<V> future, Logger log, Callback<V> callback) {
        listen(future, log, FutureController.NULL_CONTROLLER, callback);
    }

    /**
     * Extension of {@link #listen(ListenableFuture, Logger, Callback)} that also takes a {@link FutureController}
     * for controlling the {@link Future}s.
     *
     * @param future     the {@link ListenableFuture} returned by the invoking the RPC call.
     * @param log        the {@link Logger} used for logging uncaught exceptions thrown from {@link Callback#onFinish}.
     * @param callback   the {@link Callback} to handle {@link Callback#onStart} and {@link Callback#onFinish} events.
     * @param controller the {@link FutureController} used to manage the RPC futures.
     * @param <V>        the RPC result type.
     */
    public static <V> void listen(
            final ListenableFuture<V> future, final Logger log, final FutureController controller, final Callback<V> callback) {
        controller.onStart(future);
        future.addListener(new Runnable() {
            @Override
            public void run() {
                if (!controller.onStop(future)) {
                    return;
                }
                try {
                    callback.onFinish(new Result<V>(future));
                }
                catch (CancellationException e) {
                    // Not an error, don't log.
                }
                catch (Exception e) {
                    log.error(e);
                }
            }
        }, EXECUTOR);
    }

    /**
     * Callback for the {@link #listen} function.
     *
     * @param <V> the RPC result type.
     */
    public interface Callback <V> {
        /**
         * Called once the RPC call has a result (success or failure).
         *
         * <p>Call {@link Result#get()} to get the RPC result.
         */
        public abstract void onFinish(Result<V> result) throws RpcException, ExecutionException;
    }

    /**
     * Result wraps the {@link ListenableFuture} passed to {@link #listen}, providing a single
     * {@link #get} method for accessing the result of the RPC call.
     *
     * @param <V> the RPC result type.
     */
    public static class Result <V> {
        private final ListenableFuture<V> mFuture;

        private Result(ListenableFuture<V> future) {
            mFuture = future;
        }

        /**
         * Returns the result of the RPC call, or throws an exception if the RPC call was not
         * successful.
         *
         * <p>{@link RpcException}s packed in {@link ExecutionException}s thrown by the
         * {@link ListenableFuture} are unpacked and rethrown so they can be explicitly
         * handled using {@code catch} clauses by the caller.
         *
         * @return the result value.
         * @throws RpcException          if there was an error raised by the server.
         * @throws ExecutionException    if there was a non-{@link RpcException} thrown by the
         *                               {@link ListenableFuture}.
         * @throws CancellationException if the computation was cancelled.
         */
        public V get() throws RpcException, ExecutionException {
            try {
                return Uninterruptibles.getUninterruptibly(mFuture);
            }
            catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof RpcException)) {
                    throw e;
                }
                throw (RpcException)cause;
            }
        }
    }
}
