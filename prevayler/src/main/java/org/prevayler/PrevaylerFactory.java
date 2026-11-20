//Prevayler(TM) - The Free-Software Prevalence Layer.
//Copyright (C) 2001-2004 Klaus Wuestefeld
//This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//Contributions: Aleksey Aristov, Carlos Villela, Justin Sampson.

package org.prevayler;

import org.prevayler.foundation.monitor.SimpleMonitor;
import org.prevayler.foundation.serialization.JavaSerializer;
import org.prevayler.foundation.serialization.Serializer;
//import org.prevayler.foundation.serialization.SkaringaSerializer;
//import org.prevayler.foundation.serialization.XStreamSerializer;
import org.prevayler.implementation.PrevaylerDirectory;
import org.prevayler.implementation.PrevaylerImpl;
import org.prevayler.implementation.clock.MachineClock;
import org.prevayler.implementation.journal.Journal;
import org.prevayler.implementation.journal.PersistentJournal;
import org.prevayler.implementation.journal.TransientJournal;
import org.prevayler.implementation.publishing.CentralPublisher;
import org.prevayler.implementation.publishing.TransactionPublisher;
import org.prevayler.implementation.publishing.censorship.LiberalTransactionCensor;
import org.prevayler.implementation.snapshot.GenericSnapshotManager;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Provides easy access to all Prevayler configurations and implementations available in this distribution.
 * <br>By default, the Prevayler instances created by this class will write their Transactions to .journal files before executing them. The FileDescriptor.sync() method is called to make sure the Java file write-buffers have been written to the operating system. Many operating systems, including most recent versions of Linux and Windows, allow the hard-drive's write-cache to be disabled. This guarantees no executed Transaction will be lost in the event of a power shortage, for example.
 * @see Prevayler 
 */
public class PrevaylerFactory {

	private Object _prevalentSystem;
	private Clock _clock;

	private boolean _transientMode;
	private String _prevalenceDirectory;

	private Serializer _journalSerializer;
	private String _journalSuffix;
	private Map _snapshotSerializers = new HashMap();
	private String _primarySnapshotSuffix;

	/** Determines whether the Prevayler created by this factory should be transient (transientMode = true) or persistent (transientMode = false). Default is persistent. A transient Prevayler will execute its Transactions WITHOUT writing them to disk. This is useful for stand-alone applications which have a "Save" button, for example, or for running automated tests MUCH faster than with a persistent Prevayler.
	 */
	public void configureTransientMode(boolean transientMode) {
		_transientMode = transientMode;		
	}


	/** Configures the Clock that will be used by the created Prevayler (default: the machine clock). Used by tests
	 * that need fixed execution times.
	 */
	public void configureClock(Clock clock) {
		_clock = clock;
	}


	/** Configures the directory where the created Prevayler will read and write its .journal and .snapshot files. The default is a directory called "PrevalenceBase" under the current directory.
	 * @param prevalenceDirectory Will be ignored for the .snapshot files if a SnapshotManager is configured.
	 */
	public void configurePrevalenceDirectory(String prevalenceDirectory) {
		_prevalenceDirectory = prevalenceDirectory;
	}


	/** Configures the prevalent system that will be used by the Prevayler created by this factory.
	 * @param newPrevalentSystem If the default Serializer is used, this prevalentSystem must be Serializable. If another Serializer is used, this prevalentSystem must be compatible with it.
     * @see #configureSnapshotSerializer(String,Serializer)
	 */
	public void configurePrevalentSystem(Object newPrevalentSystem) {
		_prevalentSystem = newPrevalentSystem;
	}


	/**
	 * Configures the transaction journal Serializer to be used by the Prevayler created by this factory. Only one Serializer is supported at a time. If you want to change the Serializer of a system in production, you will have to take a snapshot first because the journal files written by the previous Serializer will not be read. 
	 */
	public void configureJournalSerializer(String suffix, Serializer serializer) {
		PrevaylerDirectory.checkValidJournalSuffix(suffix);

		if (_journalSerializer != null) {
			throw new IllegalStateException("Read the javadoc to this method.");
		}

		_journalSerializer = serializer;
		_journalSuffix = suffix;
	}


	/**
	 * Configure a serialization strategy for snapshots. This may be called any number of times with
	 * different suffixes to configure different strategies for reading existing snapshots. The first
	 * call to this method establishes the <i>primary</i> strategy, which will be used for writing
	 * snapshots.
	 */
	public void configureSnapshotSerializer(String suffix, Serializer serializer) {
		PrevaylerDirectory.checkValidSnapshotSuffix(suffix);
		_snapshotSerializers.put(suffix, serializer);
		if (_primarySnapshotSuffix == null) {
			_primarySnapshotSuffix = suffix;
		}
	}


	/** Returns a Prevayler created according to what was defined by calls to the configuration methods above.
	 * @throws IOException If there is trouble creating the Prevalence Base directory or reading a .journal or .snapshot file.
	 * @throws ClassNotFoundException If a class of a serialized Object is not found when reading a .journal or .snapshot file.
	 */
	public Prevayler create() throws IOException, ClassNotFoundException {
		GenericSnapshotManager snapshotManager = snapshotManager();
		TransactionPublisher publisher = publisher();
		return new PrevaylerImpl(snapshotManager, publisher, journalSerializer());
	}


    private String prevalenceDirectory() {
		return _prevalenceDirectory != null ? _prevalenceDirectory : "Prevalence";
	}


	private Object prevalentSystem() {
		if (_prevalentSystem == null) throw new IllegalStateException("The prevalent system must be configured.");
		return _prevalentSystem;
	}


	private TransactionPublisher publisher() throws IOException {
		return new CentralPublisher(_clock != null ? _clock : new MachineClock(), new LiberalTransactionCensor(), journal());
	}


	private Journal journal() throws IOException {
		if (_transientMode) {
			return new TransientJournal();
		} else {
			PrevaylerDirectory directory = new PrevaylerDirectory(prevalenceDirectory());
			return new PersistentJournal(directory, 0, 0, journalSuffix(), new SimpleMonitor(System.err));
		}
	}

	
	private Serializer journalSerializer() {
		if (_journalSerializer != null) return _journalSerializer;
		return new JavaSerializer();
	}

	private String journalSuffix() {
		return _journalSuffix != null ? _journalSuffix : "journal";
	}

	private GenericSnapshotManager snapshotManager() throws ClassNotFoundException, IOException {
		PrevaylerDirectory directory = new PrevaylerDirectory(prevalenceDirectory());
		if (!_snapshotSerializers.isEmpty())
			return new GenericSnapshotManager(_snapshotSerializers, _primarySnapshotSuffix, prevalentSystem(), directory, journalSerializer());

		String snapshotSuffix = "snapshot";
		JavaSerializer snapshotSerializer = new JavaSerializer();
		return new GenericSnapshotManager(Collections.singletonMap(snapshotSuffix, snapshotSerializer), snapshotSuffix, prevalentSystem(), directory, journalSerializer());
	}

}
