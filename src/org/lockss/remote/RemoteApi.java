/*
 * $Id: RemoteApi.java,v 1.28 2005-01-19 04:17:43 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
n
The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.remote;

import java.io.*;
import java.util.*;
import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.repository.*;
import org.lockss.util.*;
import org.apache.commons.collections.map.ReferenceMap;

/**
 * API for use by UIs and other remote agents.  Provides access to a
 * variety of daemon status and services using proxies object whose instance
 * identity is unimportant.
 */
public class RemoteApi extends BaseLockssDaemonManager {
  private static Logger log = Logger.getLogger("RemoteApi");

  static CatalogueOrderComparator coc = CatalogueOrderComparator.SINGLETON;
  static Comparator auProxyComparator = new AuProxyOrderComparator();

  static final String PARAM_AU_TREE = PluginManager.PARAM_AU_TREE;
  static final String AU_PARAM_DISPLAY_NAME =
    PluginManager.AU_PARAM_DISPLAY_NAME;

  private PluginManager pluginMgr;
  private ConfigManager configMgr;
  private RepositoryManager repoMgr;

  // cache for proxy objects
  private ReferenceMap auProxies = new ReferenceMap(ReferenceMap.WEAK,
						    ReferenceMap.WEAK);
  private ReferenceMap pluginProxies = new ReferenceMap(ReferenceMap.WEAK,
							ReferenceMap.WEAK);
  public RemoteApi() {
  }

  public void startService() {
    super.startService();
    pluginMgr = getDaemon().getPluginManager();
    configMgr = getDaemon().getConfigManager();
    repoMgr = getDaemon().getRepositoryManager();
  }

  /** Create or return an AuProxy for the AU corresponding to the auid.
   * @param auid the auid
   * @return an AuProxy for the AU, or null if no AU exists with the given
   * id.
   */
  public AuProxy findAuProxy(String auid) {
    return findAuProxy(getAuFromId(auid));
  }

  /** Create or return an AuProxy for the AU
   * @param au the AU
   * @return an AuProxy for the AU, or null if the au is null
   */
  synchronized AuProxy findAuProxy(ArchivalUnit au) {
    if (au == null) {
      return null;
    }
    AuProxy aup = (AuProxy)auProxies.get(au);
    if (aup == null) {
      aup = new AuProxy(au, this);
      auProxies.put(au, aup);
    }
    return aup;
  }

  public synchronized InactiveAuProxy findInactiveAuProxy(String auid) {
    InactiveAuProxy aup = (InactiveAuProxy)auProxies.get(auid);
    if (aup == null) {
      aup = new InactiveAuProxy(auid, this);
      auProxies.put(auid, aup);
    }
    return aup;
  }

  /** Create or return a PluginProxy for the Plugin corresponding to the id.
   * @param pluginid the plugin id
   * @return a PluginProxy for the Plugin, or null if no Plugin exists with
   * the given id.
   */
  public synchronized PluginProxy findPluginProxy(String pluginid) {
    PluginProxy pluginp = (PluginProxy)pluginProxies.get(pluginid);
    if (pluginp == null ||
	pluginp.getPlugin() != getPluginFromId(pluginid)) {
      String key = pluginMgr.pluginKeyFromId(pluginid);
      pluginMgr.ensurePluginLoaded(key);
      try {
	pluginp = new PluginProxy(pluginid, this);
      } catch (PluginProxy.NoSuchPlugin e) {
	return null;
      }
      pluginProxies.put(pluginid, pluginp);
      pluginProxies.put(pluginp.getPlugin(), pluginp);
    }
    return pluginp;
  }

  /** Create or return  PluginProxy for the Plugin
   * @param plugin the Plugin
   * @return an PluginProxy for the Plugin, or null if the plugin is null
   */
  synchronized PluginProxy findPluginProxy(Plugin plugin) {
    if (plugin == null) {
      return null;
    }
    PluginProxy pluginp = (PluginProxy)pluginProxies.get(plugin);
    if (pluginp == null) {
      pluginp = new PluginProxy(plugin, this);
      pluginProxies.put(plugin.getPluginId(), pluginp);
      pluginProxies.put(plugin, pluginp);
    }
    return pluginp;
  }

  /** Find or create an AuProxy for each au in the collection */
  List mapAusToProxies(Collection aus) {
    List res = new ArrayList();
    for (Iterator iter = aus.iterator(); iter.hasNext(); ) {
      ArchivalUnit au = (ArchivalUnit)iter.next();
      if (!pluginMgr.isInternalAu(au)) {
	AuProxy aup = findAuProxy(au);
	res.add(aup);
      }
    }
    return res;
  }

  /** Find or create a PluginProxy for each Plugin in the collection */
  List mapPluginsToProxies(Collection plugins) {
    List res = new ArrayList();
    for (Iterator iter = plugins.iterator(); iter.hasNext(); ) {
      Plugin plugin = (Plugin)iter.next();
      if (!pluginMgr.isInternalPlugin(plugin)) {
	PluginProxy pluginp = findPluginProxy(plugin);
	res.add(pluginp);
      }
    }
    return res;
  }

  // Forward useful PluginManager methods, translating between real objects
  // and proxies as appropriate.

  /**
   * Convert plugin id to key suitable for property file.  Plugin id is
   * currently the same as plugin class name, but that may change.
   * @param id the plugin id
   * @return String the plugin key
   */
  public static String pluginKeyFromId(String id) {
    return PluginManager.pluginKeyFromId(id);
  }

  /**
   * Reconfigure an AU and save the new configuration in the local config
   * file.
   * @param aup the AuProxy
   * @param auConf the new AU configuration, using simple prop keys (not
   * prefixed with org.lockss.au.<i>auid</i>)
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void setAndSaveAuConfiguration(AuProxy aup,
					Configuration auConf)
      throws ArchivalUnit.ConfigurationException, IOException {
    ArchivalUnit au = aup.getAu();
    pluginMgr.setAndSaveAuConfiguration(au, auConf);
  }

  /**
   * Create an AU and save its configuration in the local config
   * file.
   * @param pluginp the PluginProxy in which to create the AU
   * @param auConf the new AU configuration, using simple prop keys (not
   * prefixed with org.lockss.au.<i>auid</i>)
   * @return the new AuProxy
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public AuProxy createAndSaveAuConfiguration(PluginProxy pluginp,
					     Configuration auConf)
      throws ArchivalUnit.ConfigurationException, IOException {
    Plugin plugin = pluginp.getPlugin();
    ArchivalUnit au = pluginMgr.createAndSaveAuConfiguration(plugin, auConf);
    return findAuProxy(au);
  }

  /**
   * Delete AU configuration from the local config file.
   * @param aup the AuProxy
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void deleteAu(AuProxy aup)
      throws ArchivalUnit.ConfigurationException, IOException {
    if (aup.isActiveAu()) {
      ArchivalUnit au = aup.getAu();
      pluginMgr.deleteAu(au);
    } else {
      pluginMgr.deleteAuConfiguration(aup.getAuId());
    }
  }

  /**
   * Deactivate an AU
   * @param aup the AuProxy
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void deactivateAu(AuProxy aup)
      throws ArchivalUnit.ConfigurationException, IOException {
    ArchivalUnit au = aup.getAu();
    pluginMgr.deactivateAu(au);
  }

  // temporary
  public boolean isRemoveStoppedAus() {
    return pluginMgr.isRemoveStoppedAus();
  }

  /**
   * Return the stored config info for an AU (from config file, not from
   * AU instance).
   * @param aup the AuProxy
   * @return the AU's Configuration, with unprefixed keys.
   */
  public Configuration getStoredAuConfiguration(AuProxy aup) {
    return pluginMgr.getStoredAuConfiguration(aup.getAuId());
  }

  /**
   * Return the current config info for an AU (from current configuration)
   * @param aup the AuProxy
   * @return the AU's Configuration, with unprefixed keys.
   */
  public Configuration getCurrentAuConfiguration(AuProxy aup) {
    return pluginMgr.getCurrentAuConfiguration(aup.getAuId());
  }

  /**
   * Return a list of AuProxies for all configured ArchivalUnits.
   * @return the List of AuProxies
   */
  public List getAllAus() {
    return mapAusToProxies(pluginMgr.getAllAus());
  }

  public List getInactiveAus() {
    Collection inactiveAuIds = pluginMgr.getInactiveAuIds();
    if (inactiveAuIds == null || inactiveAuIds.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    List res = new ArrayList();
    for (Iterator iter = inactiveAuIds.iterator(); iter.hasNext(); ) {
      String auid = (String)iter.next();
      if (!pluginMgr.isInternalAu(pluginMgr.getAuFromId(auid))) {
	res.add(findInactiveAuProxy(auid));
      }
    }
    Collections.sort(res, auProxyComparator);
    return res;
  }

  /** Return all the known titles from the title db */
  public List findAllTitles() {
    return pluginMgr.findAllTitles();
  }

  /** Find all the plugins that support the given title */
  public Collection getTitlePlugins(String title) {
    return mapPluginsToProxies(pluginMgr.getTitlePlugins(title));
  }

  /** @return Collection of PluginProxies for all plugins that have been
   * registered.  <i>Ie</i>, that are either listed in
   * org.lockss.plugin.registry, or were loaded by a configured AU */
  public Collection getRegisteredPlugins() {
    return mapPluginsToProxies(pluginMgr.getRegisteredPlugins());
  }

  /** Return list of repository specs for all available repositories */
  public List getRepositoryList() {
    return repoMgr.getRepositoryList();
  }

  public List findExistingRepositoriesFor(String auid) {
    return repoMgr.findExistingRepositoriesFor(auid);
  }

  public PlatformInfo.DF getRepositoryDF(String repoName) {
    return repoMgr.getRepositoryDF(repoName);
  }

  ArchivalUnit getAuFromId(String auid) {
    return pluginMgr.getAuFromId(auid);
  }

  Plugin getPluginFromId(String pluginid) {
    return pluginMgr.getPlugin(pluginKeyFromId(pluginid));
  }

  String pluginIdFromAuId(String auid) {
    return pluginMgr.pluginNameFromAuId(auid);
  }

  public InputStream openCacheConfigFile(String cacheConfigFileName)
      throws FileNotFoundException {
    File cfile = configMgr.getCacheConfigFile(cacheConfigFileName);
    return new FileInputStream(cfile);
  }

  static final String AU_BACKUP_FILE_COMMENT = "# AU Configuration saved ";

  /** Open an InputStream on the local AU config file, for backup purposes */
  public InputStream getAuConfigBackupStream(String machineName)
      throws FileNotFoundException {
    InputStream fileStream =
      openCacheConfigFile(ConfigManager.CONFIG_FILE_AU_CONFIG);
    String line1 =
      AU_BACKUP_FILE_COMMENT + new Date() + " from " + machineName + "\n";
    return new SequenceInputStream(new ByteArrayInputStream(line1.getBytes()),
				   fileStream);
  }

  /** Batch create AUs from AU config backup file.
   * @param doCreate if false, AUs aren't actually configured, just checked
   * for compatibility, etc.
   * @param configBackupStream InputStream open on backup file to be restored
   * @return BatchAuStatus object describing the results.  If doCreate was
   * false, the status reflects the possibility that the AUs could be
   * created.
   * @throws RemoteApi.InvalidAuConfigBackupFile if the backup file is of
   * an unknown format, unsupported version, or contains keys this
   * operation isn't allowed to modify.
   */
  public BatchAuStatus batchAddAus(boolean doCreate,
				   InputStream configBackupStream)
      throws IOException, InvalidAuConfigBackupFile {
    BufferedInputStream bis = new BufferedInputStream(configBackupStream);
    bis.mark(10000);
    BufferedReader rdr =
      new BufferedReader(new InputStreamReader(bis,
					       Constants.DEFAULT_ENCODING));
    String line1 = rdr.readLine();
    if (line1 == null) {
      throw new InvalidAuConfigBackupFile("Uploaded file is empty");
    }
    if (!line1.startsWith(AU_BACKUP_FILE_COMMENT)) {
      log.debug("line1: " + line1);
      throw new InvalidAuConfigBackupFile("Uploaded file does not appear to be a saved AU configuration");
    }
    bis.reset();
    Properties allAuProps = new Properties();
    try {
      allAuProps.load(bis);
    } catch (Exception e) {
      log.warning("Loading AU config backup file", e);
      throw new InvalidAuConfigBackupFile("Uploaded file has illegal format: "
					  + e.getMessage());
    }
    Configuration allAuConfig =
      ConfigManager.fromPropertiesUnsealed(allAuProps);
    int ver = checkLegalBackupFile(allAuConfig);
    return batchAddAus(doCreate, false, allAuConfig);
  }

  /** Throw InvalidAuConfigBackupFile if the config is of an unknown
   * version or contains any keys that shouldn't be part of an AU config
   * backup, such as any keys outside the AU config subtree.
   * @return the file version number
   */
  int checkLegalBackupFile(Configuration config)
      throws InvalidAuConfigBackupFile {
    String verProp =
      ConfigManager.configVersionProp(ConfigManager.CONFIG_FILE_AU_CONFIG);
    if (!config.containsKey(verProp)) {
      throw new
	InvalidAuConfigBackupFile("Uploaded file has no version number");
    }
    int ver = config.getInt(verProp, 0);
    if (ver != 1) {
      throw new
	InvalidAuConfigBackupFile("Uploaded file has incompatible version " +
				  "number: " + config.get(verProp));
    }
    Configuration auConfig = config.getConfigTree(PluginManager.PARAM_AU_TREE);
    if ((config.keySet().size() - 1) != auConfig.keySet().size()) {
      String msg = "Uploaded file contains illegal keys; does not appear to be a saved AU configuration";
      log.warning(msg + ": " + config);
      throw new InvalidAuConfigBackupFile(msg);
    }
    for (Iterator iter = auConfig.keyIterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      if (PluginManager.NON_USER_SETTABLE_AU_PARAMS.contains(key)) {
	throw new InvalidAuConfigBackupFile("Uploaded file contains illegal key (" + key + "); does not appear to be a saved AU configuration");
      }
    }
    return ver;
  }

  /** Restore AU config from an AU config backup file.
   * @param allAuConfig the Configuration to be restored
   * @return BatchAuStatus object describing the results.
   * @throws RemoteApi.InvalidAuConfigBackupFile if the backup file is of
   * an unknown format, unsupported version, or contains keys this
   * operation isn't allowed to modify.
   */
  public BatchAuStatus batchAddAus(boolean doCreate,
				   boolean isReactivate,
				   Configuration allAuConfig) {
    Configuration allPlugs = allAuConfig.getConfigTree(PARAM_AU_TREE);
    BatchAuStatus bas = new BatchAuStatus();
    for (Iterator iter = allPlugs.nodeIterator(); iter.hasNext(); ) {
      String pluginKey = (String)iter.next();
      PluginProxy pluginp = findPluginProxy(pluginKey);
      // Do not dereference pluginp before null check in batchProcessOneAu()
      Configuration pluginConf = allPlugs.getConfigTree(pluginKey);
      for (Iterator auIter = pluginConf.nodeIterator(); auIter.hasNext(); ) {
	String auKey = (String)auIter.next();
	Configuration auConf = pluginConf.getConfigTree(auKey);
	String auid = PluginManager.generateAuId(pluginKey, auKey);
	bas.add(batchProcessOneAu(doCreate, isReactivate,
				  pluginp, auid, auConf));
      }
    }
    return bas;
  }

  /** Delete a batch of AUs
   * @param auids
   * @return BatchAuStatus object describing the results.
   */
  public BatchAuStatus deleteAus(List auids) {
    BatchAuStatus bas = new BatchAuStatus();
    for (Iterator iter = auids.iterator(); iter.hasNext(); ) {
      String auid = (String)iter.next();
      BatchAuStatus.Entry stat = new BatchAuStatus.Entry();
      stat.setAuid(auid);
      ArchivalUnit au = pluginMgr.getAuFromId(auid);
      if (au != null) {
	stat.setName(au.getName());
	try {
	  pluginMgr.deleteAu(au);
	  stat.setStatus("Deleted", STATUS_ORDER_NORM);
	} catch (IOException e) {
	  stat.setStatus("Not Deleted", STATUS_ORDER_WARN);
	  stat.setExplanation("Error deleting: " + e.getMessage());
	}
      } else {
	stat.setStatus("Not Found", STATUS_ORDER_WARN);
	stat.setName(auid);
      }
      bas.add(stat);
    }
    return bas;
  }

  /** Deactivate a batch of AUs
   * @param auids
   * @return BatchAuStatus object describing the results.
   */
  public BatchAuStatus deactivateAus(List auids) {
    BatchAuStatus bas = new BatchAuStatus();
    for (Iterator iter = auids.iterator(); iter.hasNext(); ) {
      String auid = (String)iter.next();
      BatchAuStatus.Entry stat = new BatchAuStatus.Entry();
      stat.setAuid(auid);
      ArchivalUnit au = pluginMgr.getAuFromId(auid);
      if (au != null) {
	stat.setName(au.getName());
	try {
	  pluginMgr.deactivateAu(au);
	  stat.setStatus("Deactivated", STATUS_ORDER_NORM);
	} catch (IOException e) {
	  stat.setStatus("Not Deactivated", STATUS_ORDER_WARN);
	  stat.setExplanation("Error deleting: " + e.getMessage());
	}
      } else {
	stat.setStatus("Not Found", STATUS_ORDER_WARN);
	stat.setName(auid);
      }
      bas.add(stat);
    }
    return bas;
  }

  /** Canonicalize a configuration so we can check it for equality with
   * another Configuration.  This is necessary both to handle parameters
   * whose value is the default (but which might be missing from the other
   * Configuration), and values that are equivalent but not equal (such as
   * case differences).  (This canonicalization would make more sense, and
   * could be moved into Configuration, where it would be less
   * out-of-place, if configuration parameters had an associated type and
   * default.  They do have type in the context of AU config params
   * (ConfigParamDescr), but we don't have that information here. */
  Configuration normalizedAuConfig(Configuration auConfig) {
    Configuration res = auConfig.copy();
    normalizeBoolean(res, PluginManager.AU_PARAM_DISABLED, false);
    res.removeConfigTree(PluginManager.AU_PARAM_RESERVED);
    return res;
  }

  void normalizeBoolean(Configuration auConfig, String param, boolean dfault) {
    auConfig.put(param, boolString(auConfig.getBoolean(param, dfault)));
  }

  String boolString(boolean b) {
    return b ? "true" : "false";
  }

  BatchAuStatus.Entry batchProcessOneAu(boolean doCreate,
					boolean isReactivate,
					PluginProxy pluginp,
					String auid,
					Configuration auConfig) {
    BatchAuStatus.Entry stat = new BatchAuStatus.Entry(auid);
    stat.setAuid(auid);
    stat.setRepoNames(repoMgr.findExistingRepositoriesFor(auid));
    Configuration oldConfig = pluginMgr.getStoredAuConfiguration(auid);
    String name = null;
    if (oldConfig != null) {
      name = oldConfig.get(AU_PARAM_DISPLAY_NAME);
    }
    if (name == null) {
      name = auConfig.get(AU_PARAM_DISPLAY_NAME);
    }
    stat.setName(name);

    if (pluginp == null) {
      stat.setStatus("Error", STATUS_ORDER_ERROR);
      stat.setExplanation("Plugin not found: " +
			  PluginManager.pluginNameFromAuId(auid));
      return stat;
    }
    if (isReactivate) {
      // make it look like we are just adding a new one
      auConfig = oldConfig;
      if (auConfig.isSealed()) {
	auConfig = auConfig.copy();
      }
      auConfig.put(PluginManager.AU_PARAM_DISABLED, "false");
      oldConfig = null;
    }
    if (oldConfig != null && !oldConfig.isEmpty()) {
      // have current config, check for disagreement, never create
      stat.setConfig(oldConfig);
      Configuration normOld = normalizedAuConfig(oldConfig);
      Configuration normNew = normalizedAuConfig(auConfig);
      ArchivalUnit au = pluginMgr.getAuFromId(auid);
      if (au != null) {
	stat.setName(au.getName());
      }
      if (normOld.equals(normNew)) {
	log.debug("Restore: same config: " + auid);
	stat.setStatus("Exists", STATUS_ORDER_LOW);
	stat.setExplanation("Already Exists");
      } else {
	log.debug("Restore: conflicting config: " + auid +
		  ", current: " + normOld + ", new: " + normNew);
	stat.setStatus("Conflict", STATUS_ORDER_ERROR);
	Set diffKeys = normNew.differentKeys(normOld);
	StringBuffer sb = new StringBuffer();
	sb.append("Conflict:<br>");
	for (Iterator iter = diffKeys.iterator(); iter.hasNext(); ) {
	  String key = (String)iter.next();
	  String foo = "Key: " + key + ", current=" + normOld.get(key) +
	    ", file=" + normNew.get(key) + "<br>";
	  sb.append(foo);
	}
	stat.setExplanation(sb.toString());
      }
    } else if (getAuFromId(auid) != null) {
      // no current config, but AU exists
      stat.setConfig(getAuFromId(auid).getConfiguration());
      stat.setStatus("Error", STATUS_ORDER_ERROR);
      stat.setExplanation("Internal inconsistency: " +
			  "AU exists but is not in config file");
    } else if (!AuUtil.isConfigCompatibleWithPlugin(auConfig,
						    pluginp.getPlugin())) {
      // no current config, new config not compatible with plugin
      stat.setStatus("Error", STATUS_ORDER_ERROR);
      stat.setExplanation("Incompatible with plugin " +
			  pluginp.getPlugin().getPluginName());
      stat.setConfig(auConfig);
    } else {
      // no current config, try to create (maybe)
      try {
	stat.setConfig(auConfig);
	if (auConfig.getBoolean(PluginManager.AU_PARAM_DISABLED, false)) {
	  if (doCreate) {
	    log.debug("Restore: inactive: " + auid);
	    pluginMgr.updateAuConfigFile(auid, auConfig);
	    stat.setStatus("Added (inactive)", STATUS_ORDER_NORM);
	  } else {
	    stat.setStatus(null, STATUS_ORDER_NORM);
	  }
	} else {
	  if (doCreate) {
	    log.debug("Restore: active: " + auid);
	    AuProxy aup = createAndSaveAuConfiguration(pluginp, auConfig);
	    stat.setStatus("Added", STATUS_ORDER_NORM);
	    stat.setName(aup.getName());
	  } else {
	    stat.setStatus(null, STATUS_ORDER_NORM);
	  }
	}
      } catch (ArchivalUnit.ConfigurationException e) {
	log.warning("batchProcessOneAu", e);
	log.warning("batchProcessOneAu: " + auid + ", " + auConfig);
	stat.setStatus("Configuration Error", STATUS_ORDER_ERROR);
	stat.setExplanation(e.getMessage());
      } catch (IOException e) {
	stat.setStatus("I/O Error", STATUS_ORDER_ERROR);
	stat.setExplanation(e.getMessage());
      }
    }
    if (stat.getName() == null) {
      stat.setName("Unknown");
    }
    return stat;
  }

  /** Find all AUs in the union of the sets and return a BatchAuStatus with
   * a BatchAuStatus.Entry for each AU indicating whether it could be created,
   * already exists, conflicts, etc.
   */
  public BatchAuStatus findAusInSetsToAdd(Collection sets) {
    BatchAuStatus bas = new BatchAuStatus();
    Set tcs = findAusInSets(sets);
    return findAusInSetsToAdd(bas, tcs.iterator());
  }

  public BatchAuStatus findAusInSetToAdd(TitleSet ts) {
    BatchAuStatus bas = new BatchAuStatus();
    return findAusInSetsToAdd(bas, ts.getTitles().iterator());
  }

  private BatchAuStatus findAusInSetsToAdd(BatchAuStatus bas, Iterator iter) {
    while (iter.hasNext()) {
      TitleConfig tc = (TitleConfig)iter.next();
      BatchAuStatus.Entry stat;
      String plugName = tc.getPluginName();
      PluginProxy pluginp = findPluginProxy(plugName);
      if (pluginp == null) {
	stat = new BatchAuStatus.Entry();
	stat.setStatus("Error", STATUS_ORDER_ERROR);
	stat.setExplanation("Plugin not found: " + plugName);
      } else {
	String auid = pluginMgr.generateAuId(pluginp.getPlugin(),
					     tc.getConfig());
	stat = batchProcessOneAu(false, false, pluginp, auid, tc.getConfig());
      }
      stat.setTitleConfig(tc);
      if ("Unknown".equalsIgnoreCase(stat.getName())) {
	stat.setName(tc.getDisplayName());
      }
      bas.add(stat);
    }
    return bas;
  }

  /** Find all AUs in the union of the sets and return a BatchAuStatus with
   * a BatchAuStatus.Entry for each AU indicating whether it could be deleted,
   * does not exist, etc.
   */
  public BatchAuStatus findAusInSetsToDelete(Collection sets) {
    BatchAuStatus bas = new BatchAuStatus();
    Set tcs = findAusInSets(sets);
    return findAusInSetsToDelete(bas, tcs.iterator());
  }

  public BatchAuStatus findAusInSetToDelete(TitleSet ts) {
    BatchAuStatus bas = new BatchAuStatus();
    return findAusInSetsToDelete(bas, ts.getTitles().iterator());
  }

  private BatchAuStatus findAusInSetsToDelete(BatchAuStatus bas,
					      Iterator iter) {
    while (iter.hasNext()) {
      TitleConfig tc = (TitleConfig)iter.next();
      BatchAuStatus.Entry stat = new BatchAuStatus.Entry();
      String plugName = tc.getPluginName();
      stat.setTitleConfig(tc);
      stat.setName(tc.getDisplayName());
      PluginProxy pluginp = findPluginProxy(plugName);
      if (pluginp == null) {
	stat.setStatus("DNE", STATUS_ORDER_LOW);
	stat.setExplanation("Does not exist");
      } else {
	String auid = pluginMgr.generateAuId(pluginp.getPlugin(),
					     tc.getConfig());
	stat.setAuid(auid);
	if (pluginMgr.getAuFromId(auid) == null) {
	  stat.setStatus("DNE", STATUS_ORDER_LOW);
	  stat.setExplanation("Does not exist");
	}
      }
      bas.add(stat);
    }
    return bas;
  }

  /** Find all AUs in the union of the sets and return a BatchAuStatus with
   * a BatchAuStatus.Entry for each AU indicating whether it could be created,
   * already exists, conflicts, etc.
   */
  public BatchAuStatus findAusInSetsToActivate(Collection sets) {
    BatchAuStatus bas = new BatchAuStatus();
    Set tcs = findAusInSets(sets);
    return findAusInSetsToActivate(bas, tcs.iterator());
  }

  public BatchAuStatus findAusInSetToActivate(TitleSet ts) {
    BatchAuStatus bas = new BatchAuStatus();
    return findAusInSetsToActivate(bas, ts.getTitles().iterator());
  }

  private BatchAuStatus findAusInSetsToActivate(BatchAuStatus bas,
						Iterator iter) {
    Collection inactiveAuids = pluginMgr.getInactiveAuIds();
    while (iter.hasNext()) {
      TitleConfig tc = (TitleConfig)iter.next();
      String plugName = tc.getPluginName();
      PluginProxy pluginp = findPluginProxy(plugName);
      if (pluginp != null) {
	String auid = pluginMgr.generateAuId(pluginp.getPlugin(),
					     tc.getConfig());
	if (inactiveAuids.contains(auid)) {
	  BatchAuStatus.Entry stat =
	    batchProcessOneAu(false, true, pluginp, auid, tc.getConfig());
	  stat.setTitleConfig(tc);
	  if ("Unknown".equalsIgnoreCase(stat.getName())) {
	    stat.setName(tc.getDisplayName());
	  }
	  bas.add(stat);
	}
      }
    }
    return bas;
  }

  Set findAusInSets(Collection sets) {
    Set res = new HashSet();
    for (Iterator iter = sets.iterator(); iter.hasNext(); ) {
      TitleSet ts = (TitleSet)iter.next();
      try {
	res.addAll(ts.getTitles());
      } catch (Exception e) {
	log.error("Error evaluating TitleSet", e);
      }
    }
    return res;
  }

  /** Object describing the status of a batch AU config operation
   * (completed or potential).  Basically a list of {@link
   * RemoteApi.BatchAuStatus.Entry}, one for each AU */
  public static class BatchAuStatus {
    private List statusList = new ArrayList();
    private List sortedList;
    private int ok = 0;

    public List getStatusList() {
      if (sortedList == null) {
	Collections.sort(statusList);
	sortedList = statusList;
      }
      return sortedList;
    }
    public int getOkCnt() {
      return ok;
    }
    void add(BatchAuStatus.Entry status) {
      sortedList = null;
      statusList.add(status);
      if (status.order == STATUS_ORDER_NORM) {
	ok++;
      }
    }
    public boolean hasOk() {
      List lst = getStatusList();
      for (Iterator iter = lst.iterator(); iter.hasNext(); ) {
	BatchAuStatus.Entry status = (BatchAuStatus.Entry)iter.next();
	if (status.isOk()) {
	  return true;
	}
      }
      return false;
    }

    public boolean hasNotOk() {
      List lst = getStatusList();
      for (Iterator iter = lst.iterator(); iter.hasNext(); ) {
	BatchAuStatus.Entry status = (BatchAuStatus.Entry)iter.next();
	if (!status.isOk()) {
	  return true;
	}
      }
      return false;
    }

  /** Object describing result or possibility of restor(e/ing) a single
   * AU from a saved or title configuration. */
  public static class Entry implements Comparable {
    private String auid;
    private String name;
    private String status;
    private String explanation;
    private TitleConfig tc;
    private Configuration config;
    private List repoNames;
    private int order = 0;

    Entry() {
    }
    Entry(String auid) {
      this.auid = auid;
    }
    public String getAuId() {
      return auid;
    }
    public String getName() {
      return name;
    }
    public String getStatus() {
      return status;
    }
    public boolean isOk() {
      return status == null;
    }
    public String getExplanation() {
      return explanation;
    }
    public TitleConfig getTitleConfig() {
      return tc;
    }
    public Configuration getConfig() {
      if (config != null) {
	return config;
      }
      if (tc != null) {
	return tc.getConfig();
      }
      return null;
    }
    public List getRepoNames() {
      return repoNames;
    }
    public void setRepoNames(List lst) {
      repoNames = lst;
    }
    void setStatus(String s, int order) {
      this.status = s;
      this.order = order;
    }
    void setName(String s) {
      this.name = s;
    }
    void setAuid(String auid) {
      this.auid = auid;
    }
    void setExplanation(String s) {
      this.explanation = s;
    }
    void setTitleConfig(TitleConfig tc) {
      this.tc = tc;
    }
    void setConfig(Configuration config) {
      this.config = config;
    }
    public int compareTo(Object o) {
      Entry ostat = (Entry)o;
      int res = order - ostat.order;
      if (res == 0) {
	res = coc.compare(getName(), ostat.getName());
      }
      return res;
    }
  }
  }
  static int STATUS_ORDER_ERROR = 1;
  static int STATUS_ORDER_WARN = 2;
  static int STATUS_ORDER_NORM = 3;
  static int STATUS_ORDER_LOW = 4;

  /** Exception thrown if the uploaded AU config backup file isn't valid */
  public static class InvalidAuConfigBackupFile extends Exception {
    public InvalidAuConfigBackupFile(String message) {
      super(message);
    }
  }

  /** Comparator for sorting AuProxy lists.  Not suitable for use in a
   * TreeSet unless changed to never return 0. */
  static class AuProxyOrderComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      AuProxy a1 = (AuProxy)o1;
      AuProxy a2 = (AuProxy)o2;
      int res = coc.compare(a1.getName(), a2.getName());
      if (res == 0) {
	res = a1.getAuId().compareTo(a2.getAuId());
      }
      return res;
    }
  }

}
