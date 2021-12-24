package org.droolsassert.util;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.droolsassert.util.ReentrantFileLock.newReentrantFileLockFactory;
import static org.droolsassert.util.ReentrantFileLock.newReentrantResourceLockFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.ConfigurationFactoryData;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.DirectFileRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.DirectWriteRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.PatternProcessor;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.RolloverListener;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.net.Advertiser;
import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.core.util.FileUtils;

/**
 * This is copy of <a href="https://logging.apache.org/log4j/2.x/manual/appenders.html#RollingFileAppender">RollingFileAppender</a> v.2.15.0 with additional file based locking
 * functionality to make it possible to populate to the same log file from different JVMs.<br>
 * <p>
 * <h2>Additional parameters:</h2>
 * <p>
 * <b>fileLock</b> - path to the file to be used for shared locking (will be created if needed). The same lock can be used for different appenders.
 * <p>
 * <b>resourceLock</b> - path to the resource to be used for shared locking (must not be packed within archive).
 * <p>
 * <b>messageQueueSize</b> - message queue size to be used as a buffer for asynchronous processing to soften heavy file locking operations time<br>
 * default 1K.
 * <p>
 * <b>writerThreadKeepAliveTimeSec</b> - <i>if value is greater than zero</i>, it is used as a time to shut down background non-daemon writer thread guarantee messages flush to the
 * file system in the background thread but not prevent normal JVM shutdown when idle. Rare messages may experience short delays (~0.3s) starting worker thread. <i>If value is
 * zero</i>, endless daemon worker thread is started not preventing normal JVM shutdown but without any guarantee of messages processing before shutdown. This approach has no write
 * time delays and continuous worker thread recreation after idle times (suitable for long running applications).<br>
 * default 3
 * 
 */
@Plugin(name = SharedRollingFileAppender.PLUGIN_NAME, category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class SharedRollingFileAppender extends AbstractOutputStreamAppender<RollingFileManager> implements RolloverListener {
	
	public static final String PLUGIN_NAME = "SharedRollingFile";
	
	public static class Builder<B extends Builder<B>> extends AbstractOutputStreamAppender.Builder<B>
			implements org.apache.logging.log4j.core.util.Builder<SharedRollingFileAppender> {
		
		@PluginBuilderAttribute
		private String fileName;
		@PluginBuilderAttribute
		@Required
		private String filePattern;
		@PluginBuilderAttribute
		private boolean append = true;
		@PluginBuilderAttribute
		private boolean locking;
		@PluginElement("Policy")
		@Required
		private TriggeringPolicy policy;
		@PluginElement("Strategy")
		private RolloverStrategy strategy;
		@PluginBuilderAttribute
		private boolean advertise;
		@PluginBuilderAttribute
		private String advertiseUri;
		@PluginBuilderAttribute
		private boolean createOnDemand;
		@PluginBuilderAttribute
		private String filePermissions;
		@PluginBuilderAttribute
		private String fileOwner;
		@PluginBuilderAttribute
		private String fileGroup;
		
		@PluginBuilderAttribute
		private String fileLock;
		@PluginBuilderAttribute
		private String resourceLock;
		@PluginBuilderAttribute
		private String messageQueueSize = "1000";
		@PluginBuilderAttribute
		private String writerThreadKeepAliveTimeSec = "3";
		
		@Override
		public SharedRollingFileAppender build() {
			// Even though some variables may be annotated with @Required, we must still perform validation here for
			// call sites that build builders programmatically.
			final boolean isBufferedIo = isBufferedIo();
			final int bufferSize = getBufferSize();
			if (getName() == null) {
				LOGGER.error("RollingFileAppender '{}': No name provided.", getName());
				return null;
			}
			
			if (!isBufferedIo && bufferSize > 0) {
				LOGGER.warn("RollingFileAppender '{}': The bufferSize is set to {} but bufferedIO is not true", getName(), bufferSize);
			}
			
			if (filePattern == null) {
				LOGGER.error("RollingFileAppender '{}': No file name pattern provided.", getName());
				return null;
			}
			
			if (policy == null) {
				LOGGER.error("RollingFileAppender '{}': No TriggeringPolicy provided.", getName());
				return null;
			}
			
			if (strategy == null) {
				if (fileName != null) {
					strategy = DefaultRolloverStrategy.newBuilder()
							.withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
							.withConfig(getConfiguration())
							.build();
				} else {
					strategy = DirectWriteRolloverStrategy.newBuilder()
							.withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
							.withConfig(getConfiguration())
							.build();
				}
			} else if (fileName == null && !(strategy instanceof DirectFileRolloverStrategy)) {
				LOGGER.error("RollingFileAppender '{}': When no file name is provided a {} must be configured", getName(), DirectFileRolloverStrategy.class.getSimpleName());
				return null;
			}
			
			ReentrantFileLock lock = newReentrantFileLock(fileLock, resourceLock);
			lock.lock();
			try {
				final Layout<? extends Serializable> layout = getOrCreateLayout();
				final SharedRollingFileManager manager = SharedRollingFileManager.getFileManager(fileName, filePattern, append,
						isBufferedIo, policy, strategy, advertiseUri, layout, bufferSize, isImmediateFlush(),
						createOnDemand, filePermissions, fileOwner, fileGroup, getConfiguration());
				if (manager == null)
					return null;
				
				manager.initialize();
				manager.closeOutputStream();
				
				int keepAliveTimeSec = Integer.parseInt(writerThreadKeepAliveTimeSec);
				ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, keepAliveTimeSec == 0 ? MAX_VALUE : keepAliveTimeSec, SECONDS, new LinkedBlockingQueue<Runnable>(),
						new BasicThreadFactory.Builder().daemon(keepAliveTimeSec == 0).namingPattern(SharedRollingFileAppender.class.getSimpleName()).build());
				
				return new SharedRollingFileAppender(getName(), layout, getFilter(), manager, fileName, filePattern,
						isIgnoreExceptions(), isImmediateFlush(), advertise ? getConfiguration().getAdvertiser() : null,
						getPropertyArray(), lock, executor, new LinkedBlockingQueue<LogEvent>(Integer.parseInt(messageQueueSize)));
			} finally {
				lock.unlock();
			}
		}
		
		private ReentrantFileLock newReentrantFileLock(String fileLock, String resourceLock) {
			if (fileLock != null)
				return newReentrantFileLockFactory(fileLock).newLock(fileName);
			if (resourceLock != null)
				return newReentrantResourceLockFactory(resourceLock).newLock(fileName);
			throw new IllegalStateException("Please specify fileLock or resourceLock to initialize SharedRollingFileAppender");
		}
		
		public String getAdvertiseUri() {
			return advertiseUri;
		}
		
		public String getFileName() {
			return fileName;
		}
		
		public boolean isAdvertise() {
			return advertise;
		}
		
		public boolean isAppend() {
			return append;
		}
		
		public boolean isCreateOnDemand() {
			return createOnDemand;
		}
		
		public boolean isLocking() {
			return locking;
		}
		
		public String getFilePermissions() {
			return filePermissions;
		}
		
		public String getFileOwner() {
			return fileOwner;
		}
		
		public String getFileGroup() {
			return fileGroup;
		}
		
		public B withAdvertise(final boolean advertise) {
			this.advertise = advertise;
			return asBuilder();
		}
		
		public B withAdvertiseUri(final String advertiseUri) {
			this.advertiseUri = advertiseUri;
			return asBuilder();
		}
		
		public B withAppend(final boolean append) {
			this.append = append;
			return asBuilder();
		}
		
		public B withFileName(final String fileName) {
			this.fileName = fileName;
			return asBuilder();
		}
		
		public B withCreateOnDemand(final boolean createOnDemand) {
			this.createOnDemand = createOnDemand;
			return asBuilder();
		}
		
		public B withLocking(final boolean locking) {
			this.locking = locking;
			return asBuilder();
		}
		
		public String getFilePattern() {
			return filePattern;
		}
		
		public TriggeringPolicy getPolicy() {
			return policy;
		}
		
		public RolloverStrategy getStrategy() {
			return strategy;
		}
		
		public B withFilePattern(final String filePattern) {
			this.filePattern = filePattern;
			return asBuilder();
		}
		
		public B withPolicy(final TriggeringPolicy policy) {
			this.policy = policy;
			return asBuilder();
		}
		
		public B withStrategy(final RolloverStrategy strategy) {
			this.strategy = strategy;
			return asBuilder();
		}
		
		public B withFilePermissions(final String filePermissions) {
			this.filePermissions = filePermissions;
			return asBuilder();
		}
		
		public B withFileOwner(final String fileOwner) {
			this.fileOwner = fileOwner;
			return asBuilder();
		}
		
		public B withFileGroup(final String fileGroup) {
			this.fileGroup = fileGroup;
			return asBuilder();
		}
		
		public B withFileLock(final String FileLock) {
			this.fileLock = FileLock;
			return asBuilder();
		}
		
		public B withResourceLock(final String resourceLock) {
			this.resourceLock = resourceLock;
			return asBuilder();
		}
		
		public B withMessageQueueSize(final String messageQueueSize) {
			this.messageQueueSize = messageQueueSize;
			return asBuilder();
		}
		
		public B withWriterThreadKeepAliveTimeSec(final String writerThreadKeepAliveTimeSec) {
			this.writerThreadKeepAliveTimeSec = writerThreadKeepAliveTimeSec;
			return asBuilder();
		}
	}
	
	private final String fileName;
	private final String filePattern;
	private Object advertisement;
	private final Advertiser advertiser;
	
	private final Runnable processQueueRunnable = this::processQueue;
	private final ThreadPoolExecutor executor;
	private final LinkedBlockingQueue<LogEvent> queue;
	private final ReentrantFileLock fileLock;
	
	private SharedRollingFileAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
			final SharedRollingFileManager manager, final String fileName, final String filePattern,
			final boolean ignoreExceptions, final boolean immediateFlush, final Advertiser advertiser,
			final Property[] properties, ReentrantFileLock fileLock, ThreadPoolExecutor executor, LinkedBlockingQueue<LogEvent> queue) {
		super(name, layout, filter, ignoreExceptions, immediateFlush, properties, manager);
		if (advertiser != null) {
			final Map<String, String> configuration = new HashMap<>(layout.getContentFormat());
			configuration.put("contentType", layout.getContentType());
			configuration.put("name", name);
			advertisement = advertiser.advertise(configuration);
		}
		this.fileName = fileName;
		this.filePattern = filePattern;
		this.advertiser = advertiser;
		this.fileLock = fileLock;
		this.executor = executor;
		this.queue = queue;
		
		executor.allowCoreThreadTimeOut(true);
		manager.addRolloverListener(this);
	}
	
	protected void processQueue() {
		if (queue.isEmpty())
			return;
		
		fileLock.lock();
		try {
			getSharedRollingFileManager().openOutputStream();
			try {
				
				for (LogEvent logEvent = queue.poll(); logEvent != null; logEvent = queue.poll())
					doAppend(logEvent);
				
			} finally {
				getSharedRollingFileManager().closeOutputStream();
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot write to " + fileName, e);
		} finally {
			fileLock.unlock();
		}
	}
	
	private SharedRollingFileManager getSharedRollingFileManager() {
		return (SharedRollingFileManager) getManager();
	}
	
	@Override
	public void rolloverTriggered(String fileName) {
		checkState(fileLock.tryLock());
	}
	
	@Override
	public void rolloverComplete(String fileName) {
		try {
			getSharedRollingFileManager().awaitAsyncRollover();
		} finally {
			fileLock.unlock();
		}
	}
	
	@Override
	public boolean stop(final long timeout, final TimeUnit timeUnit) {
		setStopping();
		final boolean stopped = super.stop(timeout, timeUnit, false);
		if (advertiser != null) {
			advertiser.unadvertise(advertisement);
		}
		setStopped();
		return stopped;
	}
	
	@Override
	public void append(final LogEvent event) {
		try {
			LogEvent snapshot = event.toImmutable();
			
			queue.put(snapshot);
			
			if (executor.getQueue().isEmpty())
				executor.execute(processQueueRunnable);
		} catch (InterruptedException e) {
			currentThread().interrupt();
		}
	}
	
	/**
	 * Writes the log entry rolling over the file when required.
	 * 
	 * @param event
	 *            The LogEvent.
	 */
	protected void doAppend(final LogEvent event) {
		getManager().checkRollover(event);
		super.append(event);
	}
	
	/**
	 * Returns the File name for the Appender.
	 * 
	 * @return The file name.
	 */
	public String getFileName() {
		return fileName;
	}
	
	/**
	 * Returns the file pattern used when rolling over.
	 * 
	 * @return The file pattern.
	 */
	public String getFilePattern() {
		return filePattern;
	}
	
	/**
	 * Returns the triggering policy.
	 * 
	 * @param <T>
	 *            TriggeringPolicy type
	 * @return The TriggeringPolicy
	 */
	public <T extends TriggeringPolicy> T getTriggeringPolicy() {
		return getManager().getTriggeringPolicy();
	}
	
	/**
	 * Creates a new Builder.
	 *
	 * @return a new Builder.
	 * @since 2.7
	 */
	@PluginBuilderFactory
	public static <B extends Builder<B>> B newBuilder() {
		return new Builder<B>().asBuilder();
	}
}

class SharedRollingFileManager extends RollingFileManager {
	
	private static class FactoryData extends ConfigurationFactoryData {
		private final String fileName;
		private final String pattern;
		private final boolean append;
		private final boolean bufferedIO;
		private final int bufferSize;
		private final boolean immediateFlush;
		private final boolean createOnDemand;
		private final TriggeringPolicy policy;
		private final RolloverStrategy strategy;
		private final String advertiseURI;
		private final Layout<? extends Serializable> layout;
		private final String filePermissions;
		private final String fileOwner;
		private final String fileGroup;
		
		/**
		 * Creates the data for the factory.
		 * 
		 * @param pattern
		 *            The pattern.
		 * @param append
		 *            The append flag.
		 * @param bufferedIO
		 *            The bufferedIO flag.
		 * @param advertiseURI
		 * @param layout
		 *            The Layout.
		 * @param bufferSize
		 *            the buffer size
		 * @param immediateFlush
		 *            flush on every write or not
		 * @param createOnDemand
		 *            true if you want to lazy-create the file (a.k.a. on-demand.)
		 * @param filePermissions
		 *            File permissions
		 * @param fileOwner
		 *            File owner
		 * @param fileGroup
		 *            File group
		 * @param configuration
		 *            The configuration
		 */
		public FactoryData(final String fileName, final String pattern, final boolean append, final boolean bufferedIO,
				final TriggeringPolicy policy, final RolloverStrategy strategy, final String advertiseURI,
				final Layout<? extends Serializable> layout, final int bufferSize, final boolean immediateFlush,
				final boolean createOnDemand, final String filePermissions, final String fileOwner, final String fileGroup,
				final Configuration configuration) {
			super(configuration);
			this.fileName = fileName;
			this.pattern = pattern;
			this.append = append;
			this.bufferedIO = bufferedIO;
			this.bufferSize = bufferSize;
			this.policy = policy;
			this.strategy = strategy;
			this.advertiseURI = advertiseURI;
			this.layout = layout;
			this.immediateFlush = immediateFlush;
			this.createOnDemand = createOnDemand;
			this.filePermissions = filePermissions;
			this.fileOwner = fileOwner;
			this.fileGroup = fileGroup;
		}
		
		public TriggeringPolicy getTriggeringPolicy() {
			return this.policy;
		}
		
		public RolloverStrategy getRolloverStrategy() {
			return this.strategy;
		}
		
		public String getPattern() {
			return pattern;
		}
	}
	
	private static class SharedRollingFileManagerFactory implements ManagerFactory<SharedRollingFileManager, FactoryData> {
		
		@Override
		public SharedRollingFileManager createManager(final String name, final FactoryData data) {
			long size = 0;
			File file = null;
			if (data.fileName != null) {
				file = new File(data.fileName);
				
				try {
					FileUtils.makeParentDirs(file);
					final boolean created = data.createOnDemand ? false : file.createNewFile();
					LOGGER.trace("New file '{}' created = {}", name, created);
				} catch (final IOException ioe) {
					LOGGER.error("Unable to create file " + name, ioe);
					return null;
				}
				size = data.append ? file.length() : 0;
			}
			
			try {
				final int actualSize = data.bufferedIO ? data.bufferSize : Constants.ENCODER_BYTE_BUFFER_SIZE;
				final ByteBuffer buffer = ByteBuffer.wrap(new byte[actualSize]);
				final OutputStream os = data.createOnDemand || data.fileName == null ? null : new FileOutputStream(data.fileName, data.append);
				// LOG4J2-531 create file first so time has valid value.
				final long initialTime = file == null || !file.exists() ? 0 : initialFileTime(file);
				final boolean writeHeader = file != null && file.exists() && file.length() == 0;
				
				final SharedRollingFileManager rm = new SharedRollingFileManager(data.getLoggerContext(), data.fileName, data.pattern, os,
						data.append, data.createOnDemand, size, initialTime, data.policy, data.strategy, data.advertiseURI,
						data.layout, data.filePermissions, data.fileOwner, data.fileGroup, writeHeader, buffer);
				if (os != null && rm.isAttributeViewEnabled()) {
					rm.defineAttributeView(file.toPath());
				}
				
				return rm;
			} catch (final IOException ex) {
				LOGGER.error("RollingFileManager (" + name + ") " + ex, ex);
			}
			return null;
		}
	}
	
	private static final FileTime EPOCH = FileTime.fromMillis(0);
	private static SharedRollingFileManagerFactory factory = new SharedRollingFileManagerFactory();
	
	/**
	 * Returns a RollingFileManager.
	 * 
	 * @param fileName
	 *            The file name.
	 * @param pattern
	 *            The pattern for rolling file.
	 * @param append
	 *            true if the file should be appended to.
	 * @param bufferedIO
	 *            true if data should be buffered.
	 * @param policy
	 *            The TriggeringPolicy.
	 * @param strategy
	 *            The RolloverStrategy.
	 * @param advertiseURI
	 *            the URI to use when advertising the file
	 * @param layout
	 *            The Layout.
	 * @param bufferSize
	 *            buffer size to use if bufferedIO is true
	 * @param immediateFlush
	 *            flush on every write or not
	 * @param createOnDemand
	 *            true if you want to lazy-create the file (a.k.a. on-demand.)
	 * @param filePermissions
	 *            File permissions
	 * @param fileOwner
	 *            File owner
	 * @param fileGroup
	 *            File group
	 * @param configuration
	 *            The configuration.
	 * @return A RollingFileManager.
	 */
	public static SharedRollingFileManager getFileManager(final String fileName, final String pattern, final boolean append,
			final boolean bufferedIO, final TriggeringPolicy policy, final RolloverStrategy strategy,
			final String advertiseURI, final Layout<? extends Serializable> layout, final int bufferSize,
			final boolean immediateFlush, final boolean createOnDemand,
			final String filePermissions, final String fileOwner, final String fileGroup,
			final Configuration configuration) {
		
		if (strategy instanceof DirectWriteRolloverStrategy && fileName != null) {
			LOGGER.error("The fileName attribute must not be specified with the DirectWriteRolloverStrategy");
			return null;
		}
		final String name = fileName == null ? pattern : fileName;
		return narrow(SharedRollingFileManager.class, getManager(name, new FactoryData(fileName, pattern, append,
				bufferedIO, policy, strategy, advertiseURI, layout, bufferSize, immediateFlush, createOnDemand,
				filePermissions, fileOwner, fileGroup, configuration), factory));
	}
	
	private static long initialFileTime(final File file) {
		final Path path = file.toPath();
		if (Files.exists(path)) {
			try {
				final BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
				final FileTime fileTime = attrs.creationTime();
				if (fileTime.compareTo(EPOCH) > 0) {
					LOGGER.debug("Returning file creation time for {}", file.getAbsolutePath());
					return fileTime.toMillis();
				}
				LOGGER.info("Unable to obtain file creation time for " + file.getAbsolutePath());
			} catch (final Exception ex) {
				LOGGER.info("Unable to calculate file creation time for " + file.getAbsolutePath() + ": " + ex.getMessage());
			}
		}
		return file.lastModified();
	}
	
	private final Semaphore rolloverSemaphore;
	
	protected SharedRollingFileManager(final LoggerContext loggerContext, final String fileName, final String pattern, final OutputStream os,
			final boolean append, final boolean createOnDemand, final long size, final long initialTime,
			final TriggeringPolicy triggeringPolicy, final RolloverStrategy rolloverStrategy,
			final String advertiseURI, final Layout<? extends Serializable> layout,
			final String filePermissions, final String fileOwner, final String fileGroup,
			final boolean writeHeader, final ByteBuffer buffer) {
		super(loggerContext, fileName, pattern, os, append, createOnDemand, size, initialTime, triggeringPolicy, rolloverStrategy, advertiseURI, layout, filePermissions, fileOwner, fileGroup,
				writeHeader, buffer);
		
		try {
			Field semaphoreField = RollingFileManager.class.getDeclaredField("semaphore");
			semaphoreField.setAccessible(true);
			rolloverSemaphore = (Semaphore) semaphoreField.get(this);
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException e) {
			throw new IllegalStateException("Cannot initialize file manager", e);
		}
	}
	
	@Override
	public boolean closeOutputStream() {
		return super.closeOutputStream();
	}
	
	public void openOutputStream() throws IOException {
		setOutputStream(createOutputStream());
	}
	
	@Override
	public void updateData(final Object data) {
		final FactoryData factoryData = (FactoryData) data;
		setRolloverStrategy(factoryData.getRolloverStrategy());
		setPatternProcessor(new PatternProcessor(factoryData.getPattern(), getPatternProcessor()));
		setTriggeringPolicy(factoryData.getTriggeringPolicy());
	}
	
	public void awaitAsyncRollover() {
		try {
			rolloverSemaphore.acquire();
			rolloverSemaphore.release();
		} catch (InterruptedException e) {
			currentThread().interrupt();
		}
	}
}