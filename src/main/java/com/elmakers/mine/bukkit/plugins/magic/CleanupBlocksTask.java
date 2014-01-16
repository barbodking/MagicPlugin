package com.elmakers.mine.bukkit.plugins.magic;

import com.elmakers.mine.bukkit.dao.BlockList;
import com.elmakers.mine.bukkit.utilities.UndoQueue;

public class CleanupBlocksTask implements Runnable
{
	protected BlockList undoBlocks;
	protected MagicController spells;
	protected UndoQueue queue;

	public CleanupBlocksTask(UndoQueue queue, MagicController spells, BlockList cleanup)
	{
		this.undoBlocks = cleanup;
		this.spells = spells;
		this.queue = queue;
	}

	public void run()
	{
		this.undoBlocks.undo(spells);
		queue.removeScheduledCleanup(undoBlocks);
	}
}
