/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.ProcessEventListener;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 12:58 PM
 */
public abstract class SvnCommand {
  private static final Logger LOG = Logger.getInstance(SvnCommand.class.getName());

  protected final Project myProject;
  private boolean myIsDestroyed;
  private int myExitCode;
  protected final GeneralCommandLine myCommandLine;
  private final File myWorkingDirectory;
  private Process myProcess;
  private OSProcessHandler myHandler;
  private final Object myLock;

  private final EventDispatcher<ProcessEventListener> myListeners = EventDispatcher.create(ProcessEventListener.class);

  // todo check version
  /*c:\Program Files (x86)\CollabNet\Subversion Client17>svn --version --quiet
  1.7.2*/

  public SvnCommand(Project project, File workingDirectory, @NotNull SvnCommandName commandName) {
    myLock = new Object();
    myProject = project;
    myCommandLine = new GeneralCommandLine();
    myWorkingDirectory = workingDirectory;
    final SvnApplicationSettings applicationSettings17 = SvnApplicationSettings.getInstance();
    myCommandLine.setExePath(applicationSettings17.getCommandLinePath());
    myCommandLine.setWorkDirectory(workingDirectory);
    myCommandLine.addParameter(commandName.getName());
  }

  public void start() {
    synchronized (myLock) {
      checkNotStarted();

      try {
        myProcess = myCommandLine.createProcess();
        myHandler = new OSProcessHandler(myProcess, myCommandLine.getCommandLineString());
        startHandlingStreams();
      } catch (Throwable t) {
        SvnVcs.getInstance(myProject).checkCommandLineVersion();
        myListeners.getMulticaster().startFailed(t);
      }
    }
  }

  private void startHandlingStreams() {
    final ProcessListener processListener = new ProcessListener() {
      public void startNotified(final ProcessEvent event) {
        // do nothing
      }

      public void processTerminated(final ProcessEvent event) {
        final int exitCode = event.getExitCode();
        try {
          setExitCode(exitCode);
          //cleanupEnv();   todo
          SvnCommand.this.processTerminated(exitCode);
        } finally {
          listeners().processTerminated(exitCode);
        }
      }

      public void processWillTerminate(final ProcessEvent event, final boolean willBeDestroyed) {
        // do nothing
      }

      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        SvnCommand.this.onTextAvailable(event.getText(), outputType);
      }
    };

    myHandler.addProcessListener(processListener);
    myHandler.startNotify();
  }

  /**
   * Wait for process termination
   */
  public void waitFor() {
    checkStarted();
    final OSProcessHandler handler;
    synchronized (myLock) {
      if (myIsDestroyed) return;
      handler = myHandler;
    }
    handler.waitFor();
  }

  protected abstract void processTerminated(int exitCode);
  protected abstract void onTextAvailable(final String text, final Key outputType);

  public void cancel() {
    synchronized (myLock) {
      checkStarted();
      destroyProcess();
    }
  }
  
  protected void setExitCode(final int code) {
    synchronized (myLock) {
      myExitCode = code;
    }
  }

  public void addListener(final ProcessEventListener listener) {
    synchronized (myLock) {
      myListeners.addListener(listener);
    }
  }

  protected ProcessEventListener listeners() {
    synchronized (myLock) {
      return myListeners.getMulticaster();
    }
  }

  public void addParameters(@NonNls @NotNull String... parameters) {
    synchronized (myLock) {
      checkNotStarted();
      myCommandLine.addParameters(parameters);
    }
  }

  public void addParameters(List<String> parameters) {
    synchronized (myLock) {
      checkNotStarted();
      myCommandLine.addParameters(parameters);
    }
  }

  public void destroyProcess() {
    synchronized (myLock) {
      myIsDestroyed = true;
      myHandler.destroyProcess();
    }
  }

  /**
   * check that process is not started yet
   *
   * @throws IllegalStateException if process has been already started
   */
  private void checkNotStarted() {
    if (isStarted()) {
      throw new IllegalStateException("The process has been already started");
    }
  }

  /**
   * check that process is started
   *
   * @throws IllegalStateException if process has not been started
   */
  protected void checkStarted() {
    if (! isStarted()) {
      throw new IllegalStateException("The process is not started yet");
    }
  }

  /**
   * @return true if process is started
   */
  public boolean isStarted() {
    synchronized (myLock) {
      return myProcess != null;
    }
  }

  protected int getExitCode() {
    synchronized (myLock) {
      return myExitCode;
    }
  }
}
