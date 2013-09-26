package com.kurento.kmf.media.internal;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.thrift.TException;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TTransportException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;

import com.kurento.kmf.common.exception.KurentoMediaFrameworkException;
import com.kurento.kmf.media.ListenerRegistration;
import com.kurento.kmf.media.MediaApiConfiguration;
import com.kurento.kmf.media.MediaPipelineFactory;
import com.kurento.kmf.media.events.MediaError;
import com.kurento.kmf.media.events.MediaEvent;
import com.kurento.kms.thrift.api.KmsError;
import com.kurento.kms.thrift.api.KmsEvent;
import com.kurento.kms.thrift.api.MediaHandlerService;
import com.kurento.kms.thrift.api.MediaHandlerService.Iface;
import com.kurento.kms.thrift.api.MediaHandlerService.Processor;

public class MediaHandlerServer {

	@Autowired
	private MediaApiConfiguration config;

	@Autowired
	private MediaPipelineFactory mediaPipelineFactory;

	@Autowired
	private MediaServerCallbackHandler handler;

	@Autowired
	private ApplicationContext applicationContext;

	public MediaHandlerServer() {
	}

	@PostConstruct
	private void init() throws KurentoMediaFrameworkException {
		start();
	}

	@PreDestroy
	private synchronized void destroy() {
		stop();
	}

	private synchronized void start() throws KurentoMediaFrameworkException {
		try {
			TNonblockingServerTransport serverTransport = new TNonblockingServerSocket(
					config.getHandlerPort());
			// TODO maybe TThreadedSelectorServer or other type of server
			TServer server = new TNonblockingServer(
					new TNonblockingServer.Args(serverTransport)
							.processor(processor));
			taskExecutor.execute(new ServerTask(server));
		} catch (TTransportException e) {
			// TODO change error code
			throw new KurentoMediaFrameworkException(
					"Could not start media handler server", e, 30000);
		}
	}

	private synchronized void stop() {
		TServer server = ServerTask.server;

		if (server != null) {
			server.stop();
			server = null;
		}
	}

	private final TaskExecutor taskExecutor = new TaskExecutor() {

		@Override
		public void execute(Runnable task) {
			task.run();
		}
	};

	private static class ServerTask implements Runnable {

		private static TServer server;

		public ServerTask(TServer tServer) {
			server = tServer;
		}

		@Override
		public void run() {
			server.serve();
		}
	}

	private final Processor<MediaHandlerService.Iface> processor = new Processor<Iface>(
			new Iface() {

				@Override
				public void onError(String callbackToken, KmsError error)
						throws TException {
					MediaError mediaError = (MediaError) applicationContext
							.getBean("mediaError", error);
					handler.onError(callbackToken, mediaError);
				}

				@Override
				public void onEvent(String callbackToken, KmsEvent event)
						throws TException {
					MediaEvent kmsEvent = (MediaEvent) applicationContext
							.getBean("mediaEvent", event);

					ListenerRegistration registration = new ListenerRegistrationImpl(
							callbackToken);
					handler.onEvent(registration,
							Long.valueOf(event.getSource().id), kmsEvent);
				}
			});
}