package dev.truebackup.app.root;

import dev.truebackup.app.root.ShellResultParcelable;

interface IRootCommandService {
    ShellResultParcelable execute(in String command);
}
