/*
 * Nextcloud Android client application
 *
 * @author Andy Scherzinger
 * @author Tobias Kaminsky
 * @author Chris Narkiewicz  <hello@ezaquarii.com>
 * Copyright (C) 2016 Andy Scherzinger
 * Copyright (C) 2017 Tobias Kaminsky
 * Copyright (C) 2016 Nextcloud
 * Copyright (C) 2016 ownCloud Inc.
 * Copyright (C) 2020 Chris Narkiewicz <hello@ezaquarii.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Html;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.google.android.material.navigation.NavigationView;
import com.nextcloud.client.account.User;
import com.nextcloud.client.di.Injectable;
import com.nextcloud.client.network.ClientFactory;
import com.nextcloud.client.onboarding.FirstRunActivity;
import com.nextcloud.client.preferences.AppPreferences;
import com.nextcloud.client.preferences.DarkMode;
import com.nextcloud.java.util.Optional;
import com.owncloud.android.MainApp;
import com.owncloud.android.R;
import com.owncloud.android.authentication.PassCodeManager;
import com.owncloud.android.datamodel.ArbitraryDataProvider;
import com.owncloud.android.datamodel.ExternalLinksProvider;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.lib.common.ExternalLink;
import com.owncloud.android.lib.common.ExternalLinkType;
import com.owncloud.android.lib.common.Quota;
import com.owncloud.android.lib.common.UserInfo;
import com.owncloud.android.lib.common.accounts.ExternalLinksOperation;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.files.SearchRemoteOperation;
import com.owncloud.android.lib.resources.status.CapabilityBooleanType;
import com.owncloud.android.lib.resources.status.OCCapability;
import com.owncloud.android.lib.resources.status.OwnCloudVersion;
import com.owncloud.android.lib.resources.users.GetUserInfoRemoteOperation;
import com.owncloud.android.operations.GetCapabilitiesOperation;
import com.owncloud.android.ui.TextDrawable;
import com.owncloud.android.ui.activities.ActivitiesActivity;
import com.owncloud.android.ui.events.AccountRemovedEvent;
import com.owncloud.android.ui.events.ChangeMenuEvent;
import com.owncloud.android.ui.events.DummyDrawerEvent;
import com.owncloud.android.ui.events.SearchEvent;
import com.owncloud.android.ui.fragment.OCFileListFragment;
import com.owncloud.android.ui.fragment.PhotoFragment;
import com.owncloud.android.ui.trashbin.TrashbinActivity;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.DrawerMenuUtil;
import com.owncloud.android.utils.FilesSyncHelper;
import com.owncloud.android.utils.ThemeUtils;
import com.owncloud.android.utils.svg.MenuSimpleTarget;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import kotlin.collections.CollectionsKt;

/**
 * Base class to handle setup of the drawer implementation including user switching and avatar fetching and fallback
 * generation.
 */
public abstract class DrawerActivity extends ToolbarActivity
    implements DisplayUtils.AvatarGenerationListener, Injectable {

    private static final String TAG = DrawerActivity.class.getSimpleName();
    private static final String KEY_IS_ACCOUNT_CHOOSER_ACTIVE = "IS_ACCOUNT_CHOOSER_ACTIVE";
    private static final String KEY_CHECKED_MENU_ITEM = "CHECKED_MENU_ITEM";
    private static final int ACTION_MANAGE_ACCOUNTS = 101;
    private static final int MENU_ORDER_ACCOUNT = 1;
    private static final int MENU_ORDER_ACCOUNT_FUNCTION = 2;
    private static final int MENU_ORDER_EXTERNAL_LINKS = 3;
    private static final int MENU_ITEM_EXTERNAL_LINK = 111;
    /**
     * menu account avatar radius.
     */
    private float mMenuAccountAvatarRadiusDimension;

    /**
     * current account avatar radius.
     */
    private float mCurrentAccountAvatarRadiusDimension;

    /**
     * other accounts avatar radius.
     */
    private float mOtherAccountAvatarRadiusDimension;

    /**
     * Reference to the drawer layout.
     */
    protected DrawerLayout mDrawerLayout;

    /**
     * Reference to the drawer toggle.
     */
    protected ActionBarDrawerToggle mDrawerToggle;

    /**
     * Reference to the navigation view.
     */
    private NavigationView mNavigationView;

    /**
     * Reference to the account chooser toggle.
     */
    private ImageView mAccountChooserToggle;

    /**
     * Reference to the middle account avatar.
     */
    private ImageView mAccountMiddleAccountAvatar;

    /**
     * Reference to the end account avatar.
     */
    private ImageView mAccountEndAccountAvatar;

    /**
     * Flag to signal if the account chooser is active.
     */
    private boolean mIsAccountChooserActive;

    /**
     * Id of the checked menu item.
     */
    private int mCheckedMenuItem = Menu.NONE;

    /**
     * accounts for the (max) three displayed accounts in the drawer header.
     */
    private List<User> mAvatars = Collections.emptyList();

    /**
     * container layout of the quota view.
     */
    private LinearLayout mQuotaView;

    /**
     * progress bar of the quota view.
     */
    private ProgressBar mQuotaProgressBar;

    /**
     * text view of the quota view.
     */
    private TextView mQuotaTextPercentage;
    private TextView mQuotaTextLink;

    /**
     * runnable that will be executed after the drawer has been closed.
     */
    private Runnable pendingRunnable;

    private ExternalLinksProvider externalLinksProvider;
    private ArbitraryDataProvider arbitraryDataProvider;

    @Inject
    AppPreferences preferences;

    @Inject
    ClientFactory clientFactory;

    /**
     * Initializes the drawer, its content and highlights the menu item with the given id.
     * This method needs to be called after the content view has been set.
     *
     * @param menuItemId the menu item to be checked/highlighted
     */
    protected void setupDrawer(int menuItemId) {
        setupDrawer();
        setDrawerMenuItemChecked(menuItemId);
    }

    /**
     * Initializes the drawer and its content.
     * This method needs to be called after the content view has been set.
     */
    protected void setupDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);

        mNavigationView = findViewById(R.id.nav_view);
        if (mNavigationView != null) {
            setupDrawerHeader();

            setupDrawerMenu(mNavigationView);

            setupQuotaElement();
        }

        setupDrawerToggle();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * initializes and sets up the drawer toggle.
     */
    private void setupDrawerToggle() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                // standard behavior of drawer is to switch to the standard menu on closing
                if (mIsAccountChooserActive) {
                    toggleAccountList();
                }
                supportInvalidateOptionsMenu();
                mDrawerToggle.setDrawerIndicatorEnabled(isDrawerIndicatorAvailable());

                if (pendingRunnable != null) {
                    new Handler().post(pendingRunnable);
                    pendingRunnable = null;
                }

                closeDrawer();
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                mDrawerToggle.setDrawerIndicatorEnabled(true);
                supportInvalidateOptionsMenu();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerToggle.setDrawerSlideAnimationEnabled(true);
    }

    /**
     * initializes and sets up the drawer header.
     */
    private void setupDrawerHeader() {
        mAccountMiddleAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_middle);
        mAccountEndAccountAvatar = (ImageView) findNavigationViewChildById(R.id.drawer_account_end);

        mAccountChooserToggle = (ImageView) findNavigationViewChildById(R.id.drawer_account_chooser_toggle);
        mAccountChooserToggle.setColorFilter(ThemeUtils.fontColor(this, true));

        if (getResources().getBoolean(R.bool.allow_profile_click)) {
            mAccountChooserToggle.setImageResource(R.drawable.ic_down);

            findNavigationViewChildById(R.id.drawer_active_user)
                    .setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            toggleAccountList();
                        }
                    });
        } else {
            mAccountChooserToggle.setVisibility(View.GONE);
        }
    }

    /**
     * setup quota elements of the drawer.
     */
    private void setupQuotaElement() {
        mQuotaView = (LinearLayout) findQuotaViewById(R.id.drawer_quota);
        mQuotaProgressBar = (ProgressBar) findQuotaViewById(R.id.drawer_quota_ProgressBar);
        mQuotaTextPercentage = (TextView) findQuotaViewById(R.id.drawer_quota_percentage);
        mQuotaTextLink = (TextView) findQuotaViewById(R.id.drawer_quota_link);
        ThemeUtils.colorProgressBar(mQuotaProgressBar, ThemeUtils.primaryColor(this));
    }

    /**
     * setup drawer content, basically setting the item selected listener.
     *
     * @param navigationView the drawers navigation view
     */
    protected void setupDrawerMenu(NavigationView navigationView) {
        navigationView.setItemIconTintList(null);

        // setup actions for drawer menu items
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
                        mDrawerLayout.closeDrawers();
                        // pending runnable will be executed after the drawer has been closed
                        pendingRunnable = new Runnable() {
                            @Override
                            public void run() {
                                onNavigationItemClicked(menuItem);
                            }
                        };
                        return true;
                    }
                });

        // handle correct state
        if (mIsAccountChooserActive) {
            navigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, true);
        } else {
            navigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, false);
        }

        User account = accountManager.getUser();
        filterDrawerMenu(navigationView.getMenu(), account);
    }

    private void filterDrawerMenu(final Menu menu, @NonNull final User user) {
            FileDataStorageManager storageManager = new FileDataStorageManager(user.toPlatformAccount(),
                                                                               getContentResolver());
        OCCapability capability = storageManager.getCapability(user.getAccountName());

        DrawerMenuUtil.filterSearchMenuItems(menu, user, getResources(), true);
        DrawerMenuUtil.filterTrashbinMenuItem(menu, user, capability);
        DrawerMenuUtil.filterActivityMenuItem(menu, capability);

        DrawerMenuUtil.setupHomeMenuItem(menu, getResources());

        DrawerMenuUtil.removeMenuItem(menu, R.id.nav_community,
                                      !getResources().getBoolean(R.bool.participate_enabled));
        DrawerMenuUtil.removeMenuItem(menu, R.id.nav_shared, !getResources().getBoolean(R.bool.shared_enabled));
        DrawerMenuUtil.removeMenuItem(menu, R.id.nav_contacts, !getResources().getBoolean(R.bool.contacts_backup)
                || !getResources().getBoolean(R.bool.show_drawer_contacts_backup));

        DrawerMenuUtil.removeMenuItem(menu, R.id.nav_synced_folders,
                getResources().getBoolean(R.bool.syncedFolder_light));
        DrawerMenuUtil.removeMenuItem(menu, R.id.nav_logout, !getResources().getBoolean(R.bool.show_drawer_logout));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(DummyDrawerEvent event) {
        unsetAllDrawerMenuItems();
    }


    private void onNavigationItemClicked(final MenuItem menuItem) {
        setDrawerMenuItemChecked(menuItem.getItemId());

        if (menuItem.getGroupId() == R.id.drawer_menu_accounts) {
            handleAccountItemClick(menuItem);
            return;
        }

        switch (menuItem.getItemId()) {
            case R.id.nav_all_files:
                if (this instanceof FileDisplayActivity) {
                    if (((FileDisplayActivity) this).getListOfFilesFragment() instanceof PhotoFragment) {
                        Intent intent = new Intent(getApplicationContext(), FileDisplayActivity.class);
                        intent.putExtra(FileDisplayActivity.DRAWER_MENU_ID, menuItem.getItemId());
                        intent.setAction(FileDisplayActivity.ALL_FILES);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    } else {
                        ((FileDisplayActivity) this).browseToRoot();
                        showFiles(false);
                        EventBus.getDefault().post(new ChangeMenuEvent());
                    }
                } else {
                    showFiles(false);
                    Intent intent = new Intent(getApplicationContext(), FileDisplayActivity.class);
                    intent.putExtra(FileDisplayActivity.DRAWER_MENU_ID, menuItem.getItemId());
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }

                break;
            case R.id.nav_favorites:
                handleSearchEvents(new SearchEvent("", SearchRemoteOperation.SearchType.FAVORITE_SEARCH),
                                   menuItem.getItemId());
                break;
            case R.id.nav_photos:
                startPhotoSearch(menuItem);
                break;
            case R.id.nav_on_device:
                EventBus.getDefault().post(new ChangeMenuEvent());
                showFiles(true);
                break;
            case R.id.nav_uploads:
                startActivity(UploadListActivity.class, Intent.FLAG_ACTIVITY_CLEAR_TOP);
                break;
            case R.id.nav_trashbin:
                startActivity(TrashbinActivity.class, Intent.FLAG_ACTIVITY_CLEAR_TOP);
                break;
            case R.id.nav_activity:
                startActivity(ActivitiesActivity.class, Intent.FLAG_ACTIVITY_CLEAR_TOP);
                break;
            case R.id.nav_notifications:
                startActivity(NotificationsActivity.class);
                break;
            case R.id.nav_synced_folders:
                startActivity(SyncedFoldersActivity.class);
                break;
            case R.id.nav_contacts:
                startActivity(ContactsPreferenceActivity.class);
                break;
            case R.id.nav_settings:
                startActivity(SettingsActivity.class);
                break;
            case R.id.nav_community:
                startActivity(CommunityActivity.class);
                break;
            case R.id.nav_logout:
                mCheckedMenuItem = -1;
                menuItem.setChecked(false);
                final Optional<User> optionalUser = getUser();
                if (optionalUser.isPresent()) {
                    UserInfoActivity.openAccountRemovalConfirmationDialog(optionalUser.get(), getSupportFragmentManager());
                }
                break;
            case R.id.nav_shared:
                handleSearchEvents(new SearchEvent("", SearchRemoteOperation.SearchType.SHARED_FILTER),
                                   menuItem.getItemId());
                break;
            case R.id.nav_recently_modified:
                handleSearchEvents(new SearchEvent("", SearchRemoteOperation.SearchType.RECENTLY_MODIFIED_SEARCH),
                                   menuItem.getItemId());
                break;
            default:
                if (menuItem.getItemId() >= MENU_ITEM_EXTERNAL_LINK &&
                    menuItem.getItemId() <= MENU_ITEM_EXTERNAL_LINK + 100) {
                    // external link clicked
                    externalLinkClicked(menuItem);
                } else {
                    Log_OC.i(TAG, "Unknown drawer menu item clicked: " + menuItem.getTitle());
                }
                break;
        }
    }

    private void startActivity(Class<? extends Activity> activity) {
        startActivity(new Intent(getApplicationContext(), activity));
    }

    private void startActivity(Class<? extends Activity> activity, int flags) {
        Intent intent = new Intent(getApplicationContext(), activity);
        intent.setFlags(flags);
        startActivity(intent);
    }

    private void handleAccountItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.drawer_menu_account_add:
                boolean isProviderOrOwnInstallationVisible = getResources()
                    .getBoolean(R.bool.show_provider_or_own_installation);

                if (isProviderOrOwnInstallationVisible) {
                    Intent firstRunIntent = new Intent(getApplicationContext(), FirstRunActivity.class);
                    firstRunIntent.putExtra(FirstRunActivity.EXTRA_ALLOW_CLOSE, true);
                    startActivity(firstRunIntent);
                } else {
                    startAccountCreation();
                }
                break;

            case R.id.drawer_menu_account_manage:
                Intent manageAccountsIntent = new Intent(getApplicationContext(), ManageAccountsActivity.class);
                startActivityForResult(manageAccountsIntent, ACTION_MANAGE_ACCOUNTS);
                break;

            default:
                accountClicked(menuItem.getItemId());
                break;
        }
    }

    private void startPhotoSearch(MenuItem menuItem) {
        SearchEvent searchEvent = new SearchEvent("image/%", SearchRemoteOperation.SearchType.PHOTO_SEARCH);

        Intent intent = new Intent(getApplicationContext(), FileDisplayActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(OCFileListFragment.SEARCH_EVENT, Parcels.wrap(searchEvent));
        intent.putExtra(FileDisplayActivity.DRAWER_MENU_ID, menuItem.getItemId());
        startActivity(intent);
    }

    private void handleSearchEvents(SearchEvent searchEvent, int menuItemId) {
        if (this instanceof FileDisplayActivity) {
            if (((FileDisplayActivity) this).getListOfFilesFragment() instanceof PhotoFragment) {
                Intent intent = new Intent(getApplicationContext(), FileDisplayActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.setAction(Intent.ACTION_SEARCH);
                intent.putExtra(OCFileListFragment.SEARCH_EVENT, Parcels.wrap(searchEvent));
                intent.putExtra(FileDisplayActivity.DRAWER_MENU_ID, menuItemId);
                startActivity(intent);
            } else {
                EventBus.getDefault().post(searchEvent);
            }
        } else {
            Intent intent = new Intent(getApplicationContext(), FileDisplayActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setAction(Intent.ACTION_SEARCH);
            intent.putExtra(OCFileListFragment.SEARCH_EVENT, Parcels.wrap(searchEvent));
            intent.putExtra(FileDisplayActivity.DRAWER_MENU_ID, menuItemId);
            startActivity(intent);
        }
    }

    /**
     * show the file list to the user.
     *
     * @param onDeviceOnly flag to decide if all files or only the ones on the device should be shown
     */
    public abstract void showFiles(boolean onDeviceOnly);


    /**
     * sets the new/current account and restarts. In case the given account equals the actual/current account the
     * call will be ignored.
     *
     * @param hashCode HashCode of account to be set
     */
    private void accountClicked(int hashCode) {
        final User currentUser = accountManager.getUser();
        if (currentUser.hashCode() != hashCode && accountManager.setCurrentOwnCloudAccount(hashCode)) {
            fetchExternalLinks(true);
            restart();
        }
    }

    private void externalLinkClicked(MenuItem menuItem){
        for (ExternalLink link : externalLinksProvider.getExternalLink(ExternalLinkType.LINK)) {
            if (menuItem.getTitle().toString().equalsIgnoreCase(link.name)) {
                if (link.redirect) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.url));
                    DisplayUtils.startIntentIfAppAvailable(intent, this, R.string.no_browser_available);
                } else {
                    Intent externalWebViewIntent = new Intent(getApplicationContext(), ExternalSiteWebView.class);
                    externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_TITLE, link.name);
                    externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_URL, link.url);
                    externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_SHOW_SIDEBAR, true);
                    externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_MENU_ITEM_ID, menuItem.getItemId());
                    startActivity(externalWebViewIntent);
                }
            }
        }
    }

    /**
     * click method for mini avatars in drawer header.
     *
     * @param view the clicked ImageView
     */
    public void onAccountDrawerClick(View view) {
        accountClicked((int) view.getTag());
    }

    /**
     * checks if the drawer exists and is opened.
     *
     * @return <code>true</code> if the drawer is open, else <code>false</code>
     */
    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    /**
     * closes the drawer.
     */
    public void closeDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * opens the drawer.
     */
    public void openDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.openDrawer(GravityCompat.START);

            updateExternalLinksInDrawer();
            updateQuotaLink();
        }
    }

    /**
     * Enable or disable interaction with all drawers.
     *
     * @param lockMode The new lock mode for the given drawer. One of {@link DrawerLayout#LOCK_MODE_UNLOCKED},
     *                 {@link DrawerLayout#LOCK_MODE_LOCKED_CLOSED} or {@link DrawerLayout#LOCK_MODE_LOCKED_OPEN}.
     */
    public void setDrawerLockMode(int lockMode) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(lockMode);
        }
    }

    /**
     * Enable or disable the drawer indicator.
     *
     * @param enable true to enable, false to disable
     */
    public void setDrawerIndicatorEnabled(boolean enable) {
        if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(enable);
        }
    }

    /**
     * updates the account list in the drawer.
     */
    public void updateAccountList() {
        List<User> users = accountManager.getAllUsers();
        ArrayList<User> persistingAccounts = new ArrayList<>();

        for (User user: users) {
            boolean pendingForRemoval = arbitraryDataProvider.getBooleanValue(user.toPlatformAccount(),
                    ManageAccountsActivity.PENDING_FOR_REMOVAL);

            if (!pendingForRemoval) {
                persistingAccounts.add(user);
            }
        }

        if (mNavigationView != null && mDrawerLayout != null) {
            if (persistingAccounts.size() > 0) {
                repopulateAccountList(persistingAccounts);
                setAccountInDrawer(accountManager.getUser());
                mAvatars = getUserAvatars();

                // activate second/end account avatar
                final User secondUser = mAvatars.size() > 1 ? mAvatars.get(1) : null;
                if (secondUser != null) {
                    mAccountEndAccountAvatar.setTag(secondUser.hashCode());
                    DisplayUtils.setAvatar(secondUser,
                                           this,
                                           mOtherAccountAvatarRadiusDimension,
                                           getResources(),
                                           mAccountEndAccountAvatar,
                                           this);
                    mAccountEndAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountEndAccountAvatar.setVisibility(View.GONE);
                }

                // activate third/middle account avatar
                final User thirdUser = mAvatars.size() > 2 ? mAvatars.get(2) : null;
                if (thirdUser != null) {
                    mAccountMiddleAccountAvatar.setTag(thirdUser.hashCode());
                    DisplayUtils.setAvatar(thirdUser,
                                           this,
                                           mOtherAccountAvatarRadiusDimension,
                                           getResources(),
                                           mAccountMiddleAccountAvatar,
                                           this);
                    mAccountMiddleAccountAvatar.setVisibility(View.VISIBLE);
                } else {
                    mAccountMiddleAccountAvatar.setVisibility(View.GONE);
                }
            } else {
                mAccountEndAccountAvatar.setVisibility(View.GONE);
                mAccountMiddleAccountAvatar.setVisibility(View.GONE);
            }
        }
    }

    /**
     * re-populates the account list.
     *
     * @param users list of users
     */
    private void repopulateAccountList(List<User> users) {
        // remove all accounts from list
        mNavigationView.getMenu().removeGroup(R.id.drawer_menu_accounts);

        // add all accounts to list
        for (User user: users) {
            try {
                // show all accounts except the currently active one and those pending for removal

                if (!getAccount().name.equals(user.getAccountName())) {
                    MenuItem accountMenuItem = mNavigationView.getMenu().add(
                        R.id.drawer_menu_accounts,
                        user.hashCode(),
                        MENU_ORDER_ACCOUNT,
                        DisplayUtils.getAccountNameDisplayText(user))
                        .setIcon(TextDrawable.createAvatar(user.toPlatformAccount(),
                                                           mMenuAccountAvatarRadiusDimension));
                    DisplayUtils.setAvatar(user, this, mMenuAccountAvatarRadiusDimension, getResources(),
                                           accountMenuItem, this);
                }
            } catch (Exception e) {
                Log_OC.e(TAG, "Error calculating RGB value for account menu item.", e);
                mNavigationView.getMenu().add(
                    R.id.drawer_menu_accounts,
                    user.hashCode(),
                    MENU_ORDER_ACCOUNT,
                    DisplayUtils.getAccountNameDisplayText(user))
                    .setIcon(R.drawable.ic_user);
            }
        }

        // re-add add-account and manage-accounts
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_add,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.prefs_add_account)).setIcon(R.drawable.ic_account_plus);
        mNavigationView.getMenu().add(R.id.drawer_menu_accounts, R.id.drawer_menu_account_manage,
                MENU_ORDER_ACCOUNT_FUNCTION,
                getResources().getString(R.string.drawer_manage_accounts)).setIcon(R.drawable.nav_settings);

        // adding sets menu group back to visible, so safety check and setting invisible
        showMenu();
    }

    /**
     * Updates title bar and home buttons (state and icon).
     * Assumes that navigation drawer is NOT visible.
     */
    protected void updateActionBarTitleAndHomeButton(OCFile chosenFile) {
        super.updateActionBarTitleAndHomeButton(chosenFile);

        // set home button properties
        if (mDrawerToggle != null && chosenFile != null) {
            if (isRoot(chosenFile)) {
                mDrawerToggle.setDrawerIndicatorEnabled(true);
            } else {
                mDrawerToggle.setDrawerIndicatorEnabled(false);
                Drawable upArrow = getResources().getDrawable(R.drawable.ic_arrow_back);
                upArrow.setColorFilter(ThemeUtils.fontColor(this), PorterDuff.Mode.SRC_ATOP);
                mDrawerToggle.setHomeAsUpIndicator(upArrow);
            }
        } else if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(false);
        }
    }

    /**
     * sets the given account name in the drawer in case the drawer is available. The account name is shortened
     * beginning from the @-sign in the username.
     *
     * @param user the account to be set in the drawer
     */
    protected void setAccountInDrawer(User user) {
        if (mDrawerLayout != null && user != null) {
            TextView username = (TextView) findNavigationViewChildById(R.id.drawer_username);
            TextView usernameFull = (TextView) findNavigationViewChildById(R.id.drawer_username_full);

            String name = user.getAccountName();
            usernameFull.setText(DisplayUtils.convertIdn(name.substring(name.lastIndexOf('@') + 1),
                                                         false));
            usernameFull.setTextColor(ThemeUtils.fontColor(this));

            username.setText(user.toOwnCloudAccount().getDisplayName());
            username.setTextColor(ThemeUtils.fontColor(this));

            View currentAccountView = findNavigationViewChildById(R.id.drawer_current_account);
            currentAccountView.setTag(name);

            DisplayUtils.setAvatar(user, this, mCurrentAccountAvatarRadiusDimension, getResources(),
                    currentAccountView, this);

            // check and show quota info if available
            getAndDisplayUserQuota();
        }
    }

    /**
     * Toggle between standard menu and account list including saving the state.
     */
    private void toggleAccountList() {
        mIsAccountChooserActive = !mIsAccountChooserActive;
        showMenu();
    }

    /**
     * depending on the #mIsAccountChooserActive flag shows the account chooser or the standard menu.
     */
    private void showMenu() {
        if (mNavigationView != null) {
            if (mIsAccountChooserActive) {
                if (mAccountChooserToggle != null) {
                    mAccountChooserToggle.setImageResource(R.drawable.ic_up);
                }
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, true);

                if (!getResources().getBoolean(R.bool.multiaccount_support) &&
                        mNavigationView.getMenu().findItem(R.id.drawer_menu_account_add) != null) {
                    mNavigationView.getMenu().removeItem(R.id.drawer_menu_account_add);
                }

                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_external_links, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_bottom, false);
            } else {
                if (mAccountChooserToggle != null) {
                    mAccountChooserToggle.setImageResource(R.drawable.ic_down);
                }
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_accounts, false);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_standard, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_external_links, true);
                mNavigationView.getMenu().setGroupVisible(R.id.drawer_menu_bottom, true);
            }
        }
    }

    /**
     * shows or hides the quota UI elements.
     *
     * @param showQuota show/hide quota information
     */
    private void showQuota(boolean showQuota) {
        if (showQuota) {
            mQuotaView.setVisibility(View.VISIBLE);
        } else {
            mQuotaView.setVisibility(View.GONE);
        }
    }

    /**
     * configured the quota to be displayed.
     *  @param usedSpace  the used space
     * @param totalSpace the total space
     * @param relative   the percentage of space already used
     * @param quotaValue {@link GetUserInfoRemoteOperation#SPACE_UNLIMITED} or other to determinate state
     */
    private void setQuotaInformation(long usedSpace, long totalSpace, int relative, long quotaValue) {
        if (GetUserInfoRemoteOperation.SPACE_UNLIMITED == quotaValue) {
            mQuotaTextPercentage.setText(String.format(
                    getString(R.string.drawer_quota_unlimited),
                    DisplayUtils.bytesToHumanReadable(usedSpace)));
        } else {
            mQuotaTextPercentage.setText(String.format(
                    getString(R.string.drawer_quota),
                    DisplayUtils.bytesToHumanReadable(usedSpace),
                    DisplayUtils.bytesToHumanReadable(totalSpace)));
        }

        mQuotaProgressBar.setProgress(relative);

        ThemeUtils.colorProgressBar(mQuotaProgressBar, DisplayUtils.getRelativeInfoColor(this, relative));

        updateQuotaLink();
        showQuota(true);
    }

    protected void unsetAllDrawerMenuItems() {
        if (mNavigationView != null && mNavigationView.getMenu() != null) {
            Menu menu = mNavigationView.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setChecked(false);
            }
        }

        mCheckedMenuItem = Menu.NONE;
    }

    private void updateQuotaLink() {
        if (mQuotaTextLink != null) {
            if (getBaseContext().getResources().getBoolean(R.bool.show_external_links)) {
                List<ExternalLink> quotas = externalLinksProvider.getExternalLink(ExternalLinkType.QUOTA);

                float density = getResources().getDisplayMetrics().density;
                final int size = Math.round(24 * density);

                if (quotas.size() > 0) {
                    final ExternalLink firstQuota = quotas.get(0);
                    mQuotaTextLink.setText(firstQuota.name);
                    mQuotaTextLink.setClickable(true);
                    mQuotaTextLink.setVisibility(View.VISIBLE);
                    mQuotaTextLink.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent externalWebViewIntent = new Intent(getApplicationContext(), ExternalSiteWebView.class);
                            externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_TITLE, firstQuota.name);
                            externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_URL, firstQuota.url);
                            externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_SHOW_SIDEBAR, true);
                            externalWebViewIntent.putExtra(ExternalSiteWebView.EXTRA_MENU_ITEM_ID, -1);
                            startActivity(externalWebViewIntent);
                        }
                    });


                    SimpleTarget target = new SimpleTarget<Drawable>() {
                        @Override
                        public void onResourceReady(Drawable resource, GlideAnimation glideAnimation) {
                            Drawable test = resource.getCurrent();
                            test.setBounds(0, 0, size, size);
                            mQuotaTextLink.setCompoundDrawablesWithIntrinsicBounds(test, null, null, null);
                        }

                        @Override
                        public void onLoadFailed(Exception e, Drawable errorDrawable) {
                            super.onLoadFailed(e, errorDrawable);

                            Drawable test = errorDrawable.getCurrent();
                            test.setBounds(0, 0, size, size);

                            mQuotaTextLink.setCompoundDrawablesWithIntrinsicBounds(test, null, null, null);
                        }
                    };

                    DisplayUtils.downloadIcon(getUserAccountManager(),
                                              clientFactory,
                                              this,
                                              firstQuota.iconUrl,
                                              target,
                                              R.drawable.ic_link,
                                              size,
                                              size);

                } else {
                    mQuotaTextLink.setVisibility(View.GONE);
                }
            } else {
                mQuotaTextLink.setVisibility(View.GONE);
            }
        }
    }

    /**
     * checks/highlights the provided menu item if the drawer has been initialized and the menu item exists.
     *
     * @param menuItemId the menu item to be highlighted
     */
    protected void setDrawerMenuItemChecked(int menuItemId) {
        if (mNavigationView != null && mNavigationView.getMenu() != null &&
                mNavigationView.getMenu().findItem(menuItemId) != null) {

            MenuItem item = mNavigationView.getMenu().findItem(menuItemId);
            item.setChecked(true);

            // reset all tinted icons
            for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                MenuItem menuItem = mNavigationView.getMenu().getItem(i);
                if (menuItem.getIcon() != null) {
                    menuItem.getIcon().clearColorFilter();
                    if (menuItem.getGroupId() != R.id.drawer_menu_accounts
                        || menuItem.getItemId() == R.id.drawer_menu_account_add
                        || menuItem.getItemId() == R.id.drawer_menu_account_manage) {
                        ThemeUtils.tintDrawable(
                            menuItem.getIcon(), ContextCompat.getColor(this, R.color.drawer_menu_icon));
                    }
                    menuItem.setTitle(Html.fromHtml(
                        "<font color='"
                            + ThemeUtils.colorToHexString(ContextCompat.getColor(this, R.color.text_color))
                            + "'>" + menuItem.getTitle()
                            + "</font>"));
                }
            }

            int elementColor = ThemeUtils.elementColor(this);
            ThemeUtils.tintDrawable(item.getIcon(), elementColor);

            String colorHex = ThemeUtils.colorToHexString(elementColor);
            item.setTitle(Html.fromHtml("<font color='" + colorHex + "'>" + item.getTitle() + "</font>"));

            mCheckedMenuItem = menuItemId;
        } else {
            Log_OC.w(TAG, "setDrawerMenuItemChecked has been called with invalid menu-item-ID");
        }
    }

    /**
     * Retrieves and shows the user quota if available
     */
    private void getAndDisplayUserQuota() {
        // set user space information
        Thread t = new Thread(new Runnable() {
            public void run() {
                final User user = accountManager.getUser();

                if (user.isAnonymous()) {
                    return;
                }

                final Context context = MainApp.getAppContext();
                RemoteOperationResult result = new GetUserInfoRemoteOperation().execute(user.toPlatformAccount(), context);

                if (result.isSuccess() && result.getData() != null) {
                    final UserInfo userInfo = (UserInfo) result.getData().get(0);
                    final Quota quota = userInfo.getQuota();

                    if (quota != null) {
                        final long used = quota.getUsed();
                        final long total = quota.getTotal();
                        final int relative = (int) Math.ceil(quota.getRelative());
                        final long quotaValue = quota.getQuota();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (quotaValue > 0 || quotaValue == GetUserInfoRemoteOperation.SPACE_UNLIMITED
                                    || quotaValue == GetUserInfoRemoteOperation.QUOTA_LIMIT_INFO_NOT_AVAILABLE) {
                                    /*
                                     * show quota in case
                                     * it is available and calculated (> 0) or
                                     * in case of legacy servers (==QUOTA_LIMIT_INFO_NOT_AVAILABLE)
                                     */
                                    setQuotaInformation(used, total, relative, quotaValue);
                                } else {
                                    /*
                                     * quotaValue < 0 means special cases like
                                     * {@link RemoteGetUserQuotaOperation.SPACE_NOT_COMPUTED},
                                     * {@link RemoteGetUserQuotaOperation.SPACE_UNKNOWN} or
                                     * {@link RemoteGetUserQuotaOperation.SPACE_UNLIMITED}
                                     * thus don't display any quota information.
                                     */
                                    showQuota(false);
                                }
                            }
                        });
                    }
                }
            }
        });

        t.start();
    }

    public void updateExternalLinksInDrawer() {
        if (mNavigationView != null && getBaseContext().getResources().getBoolean(R.bool.show_external_links)) {
            mNavigationView.getMenu().removeGroup(R.id.drawer_menu_external_links);

            float density = getResources().getDisplayMetrics().density;
            final int size = Math.round(24 * density);
            int greyColor = ContextCompat.getColor(this, R.color.drawer_menu_icon);

            for (final ExternalLink link : externalLinksProvider.getExternalLink(ExternalLinkType.LINK)) {
                int id = mNavigationView.getMenu().add(R.id.drawer_menu_external_links,
                        MENU_ITEM_EXTERNAL_LINK + link.id, MENU_ORDER_EXTERNAL_LINKS, link.name)
                        .setCheckable(true).getItemId();

                MenuSimpleTarget target = new MenuSimpleTarget<Drawable>(id) {
                    @Override
                    public void onResourceReady(Drawable resource, GlideAnimation glideAnimation) {
                        setExternalLinkIcon(getIdMenuItem(), resource, greyColor);
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                        super.onLoadFailed(e, errorDrawable);
                        setExternalLinkIcon(getIdMenuItem(), errorDrawable, greyColor);
                    }
                };

                DisplayUtils.downloadIcon(getUserAccountManager(),
                                          clientFactory,
                                          this,
                                          link.iconUrl,
                                          target,
                                          R.drawable.ic_link,
                                          size,
                                          size);
            }

            setDrawerMenuItemChecked(mCheckedMenuItem);
        }
    }

    private void setExternalLinkIcon(int id, Drawable drawable, int greyColor) {
        MenuItem menuItem = mNavigationView.getMenu().findItem(id);

        if (menuItem != null) {
            if (drawable != null) {
                menuItem.setIcon(ThemeUtils.tintDrawable(drawable, greyColor));
            } else {
                menuItem.setIcon(R.drawable.ic_link);
            }
        }
    }

    public void updateHeaderBackground() {
        if (getAccount() != null &&
                getStorageManager().getCapability(getAccount().name).getServerBackground() != null) {
            final ViewGroup navigationHeader = (ViewGroup) findNavigationViewChildById(R.id.drawer_header_view);

            if (navigationHeader != null) {
                OCCapability capability = getStorageManager().getCapability(getAccount().name);
                String background = capability.getServerBackground();
                CapabilityBooleanType backgroundDefault = capability.getServerBackgroundDefault();
                CapabilityBooleanType backgroundPlain = capability.getServerBackgroundPlain();
                int primaryColor = ThemeUtils.primaryColor(getAccount(), false, this);

                if (backgroundDefault.isTrue() && backgroundPlain.isTrue()) {
                    // use only solid color
                    setNavigationHeaderBackground(new ColorDrawable(primaryColor), navigationHeader);
                } else if (backgroundDefault.isTrue() && backgroundPlain.isFalse()) {
                    // use nc13 background image with themed color
                    Drawable[] drawables = {new ColorDrawable(primaryColor),
                        getResources().getDrawable(R.drawable.background)};
                    LayerDrawable layerDrawable = new LayerDrawable(drawables);
                    setNavigationHeaderBackground(layerDrawable, navigationHeader);
                } else {
                    // use url
                    if (URLUtil.isValidUrl(background) || background.isEmpty()) {
                        // background image
                        SimpleTarget target = new SimpleTarget<Drawable>() {
                            @Override
                            public void onResourceReady(Drawable resource, GlideAnimation glideAnimation) {
                                Drawable[] drawables = {new ColorDrawable(primaryColor), resource};
                                LayerDrawable layerDrawable = new LayerDrawable(drawables);
                                setNavigationHeaderBackground(layerDrawable, navigationHeader);
                            }

                            @Override
                            public void onLoadFailed(Exception e, Drawable errorDrawable) {
                                Drawable[] drawables = {new ColorDrawable(primaryColor), errorDrawable};
                                LayerDrawable layerDrawable = new LayerDrawable(drawables);
                                setNavigationHeaderBackground(layerDrawable, navigationHeader);
                            }
                        };

                        int backgroundResource;
                        OwnCloudVersion ownCloudVersion = accountManager.getServerVersion(getAccount());
                        if (ownCloudVersion.compareTo(OwnCloudVersion.nextcloud_18) >= 0) {
                            backgroundResource = R.drawable.background_nc18;
                        } else {
                            backgroundResource = R.drawable.background;
                        }

                        Glide.with(this)
                                .load(background)
                                .centerCrop()
                                .placeholder(backgroundResource)
                                .error(backgroundResource)
                                .crossFade()
                                .into(target);
                    } else {
                        // plain color
                        setNavigationHeaderBackground(new ColorDrawable(primaryColor), navigationHeader);
                    }
                }
            }
        }
    }

    private void setNavigationHeaderBackground(Drawable drawable, ViewGroup navigationHeader) {
        final ImageView background = navigationHeader.findViewById(R.id.drawer_header_background);
        background.setImageDrawable(drawable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
            mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);
        }

        mCurrentAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_radius);
        mOtherAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_header_avatar_other_accounts_radius);
        mMenuAccountAvatarRadiusDimension = getResources()
                .getDimension(R.dimen.nav_drawer_menu_avatar_radius);

        externalLinksProvider = new ExternalLinksProvider(getContentResolver());
        arbitraryDataProvider = new ArbitraryDataProvider(getContentResolver());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, mIsAccountChooserActive);
        outState.putInt(KEY_CHECKED_MENU_ITEM, mCheckedMenuItem);
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mIsAccountChooserActive = savedInstanceState.getBoolean(KEY_IS_ACCOUNT_CHOOSER_ACTIVE, false);
        mCheckedMenuItem = savedInstanceState.getInt(KEY_CHECKED_MENU_ITEM, Menu.NONE);

        // (re-)setup drawer state
        showMenu();

        // check/highlight the menu item if present
        if (mCheckedMenuItem > Menu.NONE || mCheckedMenuItem < Menu.NONE) {
            setDrawerMenuItemChecked(mCheckedMenuItem);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null) {
            mDrawerToggle.syncState();
            if (isDrawerOpen()) {
                mDrawerToggle.setDrawerIndicatorEnabled(true);
            }
        }
        updateAccountList();
        updateExternalLinksInDrawer();
        updateQuotaLink();
        updateHeaderBackground();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {

            getDelegate().setLocalNightMode(DarkMode.DARK == preferences.getDarkThemeMode() ?
                                                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            getDelegate().applyDayNight();
        }
        setDrawerMenuItemChecked(mCheckedMenuItem);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // update Account list and active account if Manage Account activity replies with
        // - ACCOUNT_LIST_CHANGED = true
        // - RESULT_OK
        if (requestCode == ACTION_MANAGE_ACCOUNTS && resultCode == RESULT_OK
                && data.getBooleanExtra(ManageAccountsActivity.KEY_ACCOUNT_LIST_CHANGED, false)) {

            // current account has changed
            if (data.getBooleanExtra(ManageAccountsActivity.KEY_CURRENT_ACCOUNT_CHANGED, false)) {
                setAccount(accountManager.getCurrentAccount(), false);
                updateAccountList();
                restart();
            } else {
                updateAccountList();
            }
        } else if (requestCode == PassCodeManager.PASSCODE_ACTIVITY &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && data != null) {
            int result = data.getIntExtra(RequestCredentialsActivity.KEY_CHECK_RESULT,
                    RequestCredentialsActivity.KEY_CHECK_RESULT_FALSE);

            if (result == RequestCredentialsActivity.KEY_CHECK_RESULT_CANCEL) {
                Log_OC.d(TAG, "PassCodeManager cancelled");
                preferences.setLockTimestamp(0);
                finish();
            }
        }
    }

    /**
     * Finds a view that was identified by the id attribute from the drawer header.
     *
     * @param id the view's id
     * @return The view if found or <code>null</code> otherwise.
     */
    private View findNavigationViewChildById(int id) {
        NavigationView view = findViewById(R.id.nav_view);

        if (view != null) {
            return view.getHeaderView(0).findViewById(id);
        } else {
            return null;
        }
    }

    /**
     * Quota view can be either at navigation bottom or header
     *
     * @param id the view's id
     * @return The view if found or <code>null</code> otherwise.
     */
    private View findQuotaViewById(int id) {
        View v = ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0).findViewById(id);

        if (v != null) {
            return v;
        } else {
            return findViewById(id);
        }
    }

    /**
     * restart helper method which is called after a changing the current account.
     */
    protected abstract void restart();

    /**
     * Get list of users suitable for displaying in navigation drawer header.
     * First item is always current {@link User}. Remaining items are other
     * users possible to switch to.
     *
     * @return List of available users
     */
    @NonNull
    private List<User> getUserAvatars() {
        User currentUser = accountManager.getUser();
        List<User> availableUsers = CollectionsKt.filter(accountManager.getAllUsers(), user ->
            !TextUtils.equals(user.getAccountName(), currentUser.getAccountName()) &&
            !arbitraryDataProvider.getBooleanValue(user.toPlatformAccount(),
                                                   ManageAccountsActivity.PENDING_FOR_REMOVAL)
        );
        availableUsers.add(0, currentUser);
        return availableUsers;
    }

    @Override
    public void avatarGenerated(Drawable avatarDrawable, Object callContext) {
        if (callContext instanceof MenuItem) {
            MenuItem mi = (MenuItem) callContext;
            mi.setIcon(avatarDrawable);
        } else if (callContext instanceof ImageView) {
            ImageView iv = (ImageView) callContext;
            iv.setImageDrawable(avatarDrawable);
        }
    }

    @Override
    public boolean shouldCallGeneratedCallback(String tag, Object callContext) {
        if (callContext instanceof MenuItem) {
            MenuItem mi = (MenuItem) callContext;
            return String.valueOf(mi.getTitle()).equals(tag);
        } else if (callContext instanceof ImageView) {
            ImageView iv = (ImageView) callContext;
            return String.valueOf(iv.getTag()).equals(tag);
        }
        return false;
    }

    /**
     * Adds other listeners to react on changes of the drawer layout.
     *
     * @param listener Object interested in changes of the drawer layout.
     */
    public void addDrawerListener(DrawerLayout.DrawerListener listener) {
        if (mDrawerLayout != null) {
            mDrawerLayout.addDrawerListener(listener);
        } else {
            Log_OC.e(TAG, "Drawer layout not ready to add drawer listener");
        }
    }

    public boolean isDrawerIndicatorAvailable() {
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        if (preferences.getLockTimestamp() != 0) {
            preferences.setLockTimestamp(SystemClock.elapsedRealtime());
        }
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAccountRemovedEvent(AccountRemovedEvent event) {
        updateAccountList();

        restart();
    }

    /**
     * Retrieves external links via api from 'external' app
     */
    public void fetchExternalLinks(final boolean force) {
        if (getBaseContext().getResources().getBoolean(R.bool.show_external_links)) {
            Thread t = new Thread(() -> {
                // fetch capabilities as early as possible
                if ((getCapabilities() == null || getCapabilities().getAccountName().isEmpty())
                        && getStorageManager() != null) {
                    GetCapabilitiesOperation getCapabilities = new GetCapabilitiesOperation();
                    getCapabilities.execute(getStorageManager(), getBaseContext());
                }

                User user = accountManager.getUser();
                String name = user.getAccountName();
                if (getStorageManager() != null && getStorageManager().getCapability(name) != null &&
                        getStorageManager().getCapability(name).getExternalLinks().isTrue()) {

                    int count = arbitraryDataProvider.getIntegerValue(FilesSyncHelper.GLOBAL,
                            FileActivity.APP_OPENED_COUNT);

                    if (count > 10 || count == -1 || force) {
                        if (force) {
                            Log_OC.d("ExternalLinks", "force update");
                        }

                        arbitraryDataProvider.storeOrUpdateKeyValue(FilesSyncHelper.GLOBAL,
                                FileActivity.APP_OPENED_COUNT, "0");

                        Log_OC.d("ExternalLinks", "update via api");
                        RemoteOperation getExternalLinksOperation = new ExternalLinksOperation();
                        RemoteOperationResult result = getExternalLinksOperation.execute(user.toPlatformAccount(), this);

                        if (result.isSuccess() && result.getData() != null) {
                            externalLinksProvider.deleteAllExternalLinks();

                            ArrayList<ExternalLink> externalLinks = (ArrayList<ExternalLink>) (Object) result.getData();

                            for (ExternalLink link : externalLinks) {
                                externalLinksProvider.storeExternalLink(link);
                            }
                        }
                    } else {
                        arbitraryDataProvider.storeOrUpdateKeyValue(FilesSyncHelper.GLOBAL,
                                FileActivity.APP_OPENED_COUNT, String.valueOf(count + 1));
                    }
                } else {
                    externalLinksProvider.deleteAllExternalLinks();
                    Log_OC.d("ExternalLinks", "links disabled");
                }
                runOnUiThread(this::updateExternalLinksInDrawer);
            });

            t.start();
        }
    }
}
