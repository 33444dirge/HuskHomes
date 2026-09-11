package net.william278.huskhomes.command;

import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.PaperHuskHomes;
import net.william278.huskhomes.menu.HomeMenu;
import net.william278.huskhomes.user.CommandUser;
import net.william278.huskhomes.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Internal command bridge used by Paper Dialog actions. */
public final class HomeMenuCommand extends Command {

    public HomeMenuCommand(@NotNull HuskHomes plugin) {
        super(List.of("homemenu"), "<save|confirm> <home> ...", plugin);
    }

    @Override
    public void execute(@NotNull CommandUser executor, @NotNull String[] args) {
        if (!(executor instanceof OnlineUser user) || !(plugin instanceof PaperHuskHomes paper) || args.length == 0) {
            return;
        }
        switch (args[0].toLowerCase()) {
            case "confirm" -> openConfirmation(paper, user, args);
            case "save" -> saveChanges(executor, args);
            case "privacy" -> setPrivacy(executor, args);
            case "open" -> paper.getHomeMenu().open(user);
            default -> { }
        }
    }

    private void openConfirmation(@NotNull PaperHuskHomes paper, @NotNull OnlineUser user, @NotNull String[] args) {
        if (args.length != 3) {
            return;
        }
        final HomeMenu.ConfirmationAction action = HomeMenu.ConfirmationAction.from(args[1]);
        decodeHomeName(args[2]).ifPresent(home -> {
            if (action != null) {
                paper.getHomeMenu().openConfirmation(user, action, home);
            }
        });
    }

    private void setPrivacy(@NotNull CommandUser executor, @NotNull String[] args) {
        if (!(executor instanceof OnlineUser user) || args.length != 3) {
            return;
        }
        decodeHomeName(args[1]).ifPresent(home -> plugin.getCommand(EditHomeCommand.class)
                .ifPresent(command -> command.execute(executor, new String[]{home, "privacy", args[2]})));
    }

    private void saveChanges(@NotNull CommandUser executor, @NotNull String[] args) {
        if (!(executor instanceof OnlineUser user) || args.length < 3) {
            return;
        }
        final Optional<String> decodedHome = decodeHomeName(args[1]);
        if (decodedHome.isEmpty()) {
            return;
        }
        final String home = decodedHome.get();
        final String name = args[2].trim();
        final String description = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
        plugin.getDatabase().getHome(user, home).ifPresent(existing -> plugin.getCommand(EditHomeCommand.class)
                .ifPresent(command -> {
                    if (!description.isBlank() && !description.equals(existing.getMeta().getDescription())) {
                        command.execute(executor, new String[]{home, "description", description});
                    }
                    if (!name.isBlank() && !name.equals(existing.getName())) {
                        command.execute(executor, new String[]{home, "rename", name});
                    }
                }));
    }

    @NotNull
    private static Optional<String> decodeHomeName(@NotNull String encodedHomeName) {
        try {
            return Optional.of(new String(Base64.getUrlDecoder().decode(encodedHomeName), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
