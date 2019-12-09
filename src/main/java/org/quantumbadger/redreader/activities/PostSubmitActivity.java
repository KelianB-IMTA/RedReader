/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.activities;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;
import org.quantumbadger.redreader.R;
import org.quantumbadger.redreader.account.RedditAccount;
import org.quantumbadger.redreader.account.RedditAccountManager;
import org.quantumbadger.redreader.cache.CacheManager;
import org.quantumbadger.redreader.cache.CacheRequest;
import org.quantumbadger.redreader.common.AndroidCommon;
import org.quantumbadger.redreader.common.General;
import org.quantumbadger.redreader.common.LinkHandler;
import org.quantumbadger.redreader.common.PrefsUtility;
import org.quantumbadger.redreader.common.RRError;
import org.quantumbadger.redreader.fragments.MarkdownPreviewDialog;
import org.quantumbadger.redreader.reddit.APIResponseHandler;
import org.quantumbadger.redreader.reddit.RedditAPI;

import java.util.ArrayList;

// TODO save draft as static var (as in comments)
public class PostSubmitActivity extends BaseActivity {

	private Spinner typeSpinner, usernameSpinner;
	private EditText subredditEdit, titleEdit, textEdit;
	private CheckBox sendRepliesToInboxCheckbox;
	private CheckBox markAsNsfwCheckbox;
	private CheckBox markAsSpoilerCheckbox;

	private static final String[] postTypes = {"Link", "Self", "Upload to Imgur"};

	private static final int
			REQUEST_UPLOAD = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		PrefsUtility.applyTheme(this);

		super.onCreate(savedInstanceState);

		setTitle(R.string.submit_post_actionbar);

		// on utilise le fichier xml pour faire le design général
		final LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.post_submit, null);

		// on récupère chaque objet du XML
		typeSpinner = (Spinner)layout.findViewById(R.id.post_submit_type);
		usernameSpinner = (Spinner)layout.findViewById(R.id.post_submit_username);
		subredditEdit = (EditText)layout.findViewById(R.id.post_submit_subreddit);
		titleEdit = (EditText)layout.findViewById(R.id.post_submit_title);
		textEdit = (EditText)layout.findViewById(R.id.post_submit_body);
		sendRepliesToInboxCheckbox = (CheckBox)layout.findViewById(R.id.post_submit_send_replies_to_inbox);
		markAsNsfwCheckbox = (CheckBox) layout.findViewById(R.id.post_submit_mark_nsfw);
		markAsSpoilerCheckbox = (CheckBox) layout.findViewById(R.id.post_submit_mark_spoiler);

		// permet de récupérer l'intent qui a lancé l'activité
		final Intent intent = getIntent();
		if(intent != null) {

			// on vérifie qu'il existe un extra pour la clef subreddit
			if(intent.hasExtra("subreddit")) {

				// si oui on récupère la valeur
				final String subreddit = intent.getStringExtra("subreddit");

				// on remplit automatiquement le texte de subreddit dans ce cas.
				if(subreddit != null && subreddit.length() > 0 && !subreddit.matches("/?(r/)?all/?") && subreddit.matches("/?(r/)?\\w+/?")) {
					subredditEdit.setText(subreddit);
				}
			// ACTION_SEND sont des actions standards d'un Intent
			// getAction permet de récupérer l'action standard
			// permet d'insérer l'URL dans le corps du message. Bizarre.
			} else if(Intent.ACTION_SEND.equalsIgnoreCase(intent.getAction()) && intent.hasExtra(Intent.EXTRA_TEXT)){
				final String url = intent.getStringExtra(Intent.EXTRA_TEXT);
				textEdit.setText(url);
			}

		// dans le cas où on tourne le téléphone I suppose.
		} else if(savedInstanceState != null && savedInstanceState.containsKey("post_title")) {
			titleEdit.setText(savedInstanceState.getString("post_title"));
			textEdit.setText(savedInstanceState.getString("post_body"));
			subredditEdit.setText(savedInstanceState.getString("subreddit"));
			typeSpinner.setSelection(savedInstanceState.getInt("post_type"));
		}

		final ArrayList<RedditAccount> accounts = RedditAccountManager.getInstance(this).getAccounts();
		final ArrayList<String> usernames = new ArrayList<>();

		for(RedditAccount account : accounts) {
			if(!account.isAnonymous()) {
				usernames.add(account.username);
			}
		}
		// devrait être beaucoup plus haut
		if(usernames.size() == 0) {
			General.quickToast(this, R.string.error_toast_notloggedin);
			finish();
		}

		// on remplit les Spinner grâce à des adapters
		usernameSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, usernames));
		typeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, postTypes));

		// TODO remove the duplicate code here
		setHint();

		typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				setHint();
			}

			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		final ScrollView sv = new ScrollView(this);
		sv.addView(layout);
		setBaseActivityContentView(sv);
	}

	private void setHint() {

		final Object selected = typeSpinner.getSelectedItem();
		// permet d'upload vers Imgur, un autre réseau social bizarre OU un URL
		if(selected.equals("Link") || selected.equals("Upload to Imgur")) {
			// SHOULD BE BODY AND NOT URL
			textEdit.setHint(getString(R.string.submit_post_url_hint));
			textEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
			textEdit.setSingleLine(true);
		// à voir ce qu'on en comprend dans les actions
		} else if(selected.equals("Self")) {
			textEdit.setHint(getString(R.string.submit_post_self_text_hint));
			textEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
			textEdit.setSingleLine(false);
		} else {
			throw new RuntimeException("Unknown selection " + selected.toString());
		}

		if(selected.equals("Upload to Imgur")) {

			typeSpinner.setSelection(0); // Link
			// on envoie un code 1 à l'activité ImgurUpload.
			// on a le retour plus tard dans le code (onActivityResult)
			final Intent intent = new Intent(this, ImgurUploadActivity.class);
			startActivityForResult(intent, REQUEST_UPLOAD);
		}
	}

	// on sauvegarde les infos au cas où
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("post_title", titleEdit.getText().toString());
		outState.putString("post_body", textEdit.getText().toString());
		outState.putString("subreddit", subredditEdit.getText().toString());
		outState.putInt("post_type", typeSpinner.getSelectedItemPosition());
	}

	// un menu en plus. Pour faire un preview avant le post
	// voir sur quoi ça nous amène.
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		final MenuItem send = menu.add(R.string.comment_reply_send);
		send.setIcon(R.drawable.ic_action_send_dark);
		send.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		menu.add(R.string.comment_reply_preview);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {

		if(item.getTitle().equals(getString(R.string.comment_reply_send))) {

			String subreddit = subredditEdit.getText().toString();
			final String postTitle = titleEdit.getText().toString();
			final String text = textEdit.getText().toString();
			// vérifie qu'on a bien tous les champs remplis avant l'envoi
			if (subreddit.isEmpty()) {
				Toast.makeText(this, R.string.submit_post_specify_subreddit, Toast.LENGTH_SHORT).show();
				subredditEdit.requestFocus();
			} else if (postTitle.isEmpty()) {
				Toast.makeText(this, R.string.submit_post_title_empty, Toast.LENGTH_SHORT).show();
				titleEdit.requestFocus();
			}  else if (getString(R.string.submit_post_url_hint).equals(textEdit.getHint().toString()) && text.isEmpty()) {
				Toast.makeText(this, R.string.submit_post_url_empty, Toast.LENGTH_SHORT).show();
				textEdit.requestFocus();
			} else {
				final ProgressDialog progressDialog = new ProgressDialog(this);
				progressDialog.setTitle(getString(R.string.comment_reply_submitting_title));
				progressDialog.setMessage(getString(R.string.comment_reply_submitting_message));
				progressDialog.setIndeterminate(true);
				progressDialog.setCancelable(true);
				progressDialog.setCanceledOnTouchOutside(false);

				progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(final DialogInterface dialogInterface) {
						General.quickToast(PostSubmitActivity.this, getString(R.string.comment_reply_oncancel));
						General.safeDismissDialog(progressDialog);
					}
				});

				progressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
					public boolean onKey(final DialogInterface dialogInterface, final int keyCode, final KeyEvent keyEvent) {

						if(keyCode == KeyEvent.KEYCODE_BACK) {
							General.quickToast(PostSubmitActivity.this, getString(R.string.comment_reply_oncancel));
							General.safeDismissDialog(progressDialog);
						}

						return true;
					}
				});

				final CacheManager cm = CacheManager.getInstance(this);

				final APIResponseHandler.ActionResponseHandler handler = new APIResponseHandler.ActionResponseHandler(this) {
					@Override
					protected void onSuccess(@Nullable final String redirectUrl) {
						AndroidCommon.UI_THREAD_HANDLER.post(new Runnable() {
							@Override
							public void run() {
								General.safeDismissDialog(progressDialog);
								General.quickToast(PostSubmitActivity.this, getString(R.string.post_submit_done));

								if(redirectUrl != null) {
									LinkHandler.onLinkClicked(PostSubmitActivity.this, redirectUrl);
								}

								finish();
							}
						});
					}

					@Override
					protected void onCallbackException(Throwable t) {
						BugReportActivity.handleGlobalError(PostSubmitActivity.this, t);
					}

					@Override
					protected void onFailure(@CacheRequest.RequestFailureType int type, Throwable t, Integer status, String readableMessage) {

						final RRError error = General.getGeneralErrorForFailure(context, type, t, status, null);

						AndroidCommon.UI_THREAD_HANDLER.post(new Runnable() {
							@Override
							public void run() {
								General.showResultDialog(PostSubmitActivity.this, error);
								General.safeDismissDialog(progressDialog);
							}
						});
					}

					@Override
					protected void onFailure(final APIFailureType type) {

						final RRError error = General.getGeneralErrorForFailure(context, type);

						AndroidCommon.UI_THREAD_HANDLER.post(new Runnable() {
							@Override
							public void run() {
								General.showResultDialog(PostSubmitActivity.this, error);
								General.safeDismissDialog(progressDialog);
							}
						});
					}
				};

				final boolean is_self = typeSpinner.getSelectedItem().equals("Self");

				final RedditAccount selectedAccount = RedditAccountManager.getInstance(this).getAccount((String)usernameSpinner.getSelectedItem());

				while(subreddit.startsWith("/")) subreddit = subreddit.substring(1);
				while(subreddit.startsWith("r/")) subreddit = subreddit.substring(2);
				while(subreddit.endsWith("/")) subreddit = subreddit.substring(0, subreddit.length() - 1);

				final boolean sendRepliesToInbox = sendRepliesToInboxCheckbox.isChecked();
				final boolean markAsNsfw = markAsNsfwCheckbox.isChecked();
				final boolean markAsSpoiler = markAsSpoilerCheckbox.isChecked();

				// permet d'envoyer le message via l'API Reddit !
				RedditAPI.submit(cm, handler, selectedAccount, is_self, subreddit, postTitle, text,
						sendRepliesToInbox, markAsNsfw, markAsSpoiler, this);

				progressDialog.show();
			}
			return true;

		} else if(item.getTitle().equals(getString(R.string.comment_reply_preview))) {
			MarkdownPreviewDialog.newInstance(textEdit.getText().toString()).show(getSupportFragmentManager(), null);
			return true;

		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {

		if(requestCode == REQUEST_UPLOAD) {

			if(data != null && data.getData() != null) {
				textEdit.setText(data.getData().toString());
			}
		}
	}

	@Override
	public void onBackPressed() {
		if(General.onBackPressed()) super.onBackPressed();
	}
}
