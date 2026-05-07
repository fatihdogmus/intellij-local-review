package dev.agentreview.intellij;

import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;

import java.util.UUID;

public final class VcsLogUiFactoryHelper {
  private VcsLogUiFactoryHelper() {}

  public static MainVcsLogUi createMainLogUi(VcsLogManager manager) {
    return manager.createLogUi("local-review-" + UUID.randomUUID(), null);
  }

  public static VcsLogPanel createPanel(VcsLogManager manager, MainVcsLogUi ui) {
    return new VcsLogPanel(manager, ui);
  }
}
