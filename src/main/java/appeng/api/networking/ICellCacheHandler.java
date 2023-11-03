package appeng.api.networking;

import appeng.api.storage.StorageChannel;

public interface ICellCacheHandler {
	
	long getTotalBytes();
	
	long getFreeBytes();
	
	long getUsedBytes();
	
	long getTotalTypes();
	
	long getFreeTypes();
	
	long getUsedTypes();
	
	StorageChannel getCellType();

}
