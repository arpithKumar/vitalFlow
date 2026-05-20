package com.vitalflow.app.ui.auth;

import static androidx.core.content.ContextCompat.getDrawable;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.vitalflow.app.R;
import com.vitalflow.app.data.repository.SyncRepository;
import com.vitalflow.app.databinding.FragmentLoginBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private FirebaseAuth mAuth;
    private boolean isLoginMode = true; // True = Login, False = SignUp
    private CredentialManager credentialManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    SyncRepository syncRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // 1. Initialize Credential Manager
        credentialManager = CredentialManager.create(requireContext());

        // Click Listeners
        binding.btnAuthAction.setOnClickListener(v -> handleEmailAuth());
        binding.btnGoogle.setOnClickListener(v -> launchGoogleSignIn());

        // Toggle Switcher Logic
        binding.toggleAuthMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTabLogin) {
                    updateUiMode(true);
                } else if (checkedId == R.id.btnTabSignup) {
                    updateUiMode(false);
                }
            }
        });
    }

    private void updateUiMode(boolean isLogin) {
        this.isLoginMode = isLogin;

        // Animate UI Changes
        TransitionManager.beginDelayedTransition(binding.cardLogin);

        if (isLogin) {
            binding.tvHeader.setText("Welcome Back");
            binding.tvSubHeader.setText("Please enter your details to sign in.");
            binding.btnAuthAction.setText("Log In");
        } else {
            binding.tvHeader.setText("Create Account");
            binding.tvSubHeader.setText("Join us to start your healthy journey.");
            binding.btnAuthAction.setText("Sign Up");
        }
    }

    // --- EMAIL / PASSWORD AUTH ---
    private void handleEmailAuth() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        if (isLoginMode) {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        setLoading(false);
                        if (task.isSuccessful()) {
                            navigateToHome();
                        } else {
                            Toast.makeText(getContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        setLoading(false);
                        if (task.isSuccessful()) {
                            navigateToHome();
                        } else {
                            Toast.makeText(getContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void launchGoogleSignIn() {
        setLoading(true);

        // 1. Create the Google ID Option
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show all accounts, not just previously used ones
                .setServerClientId(getString(R.string.default_web_client_id)) // From google-services.json
                .setAutoSelectEnabled(false) // Always show the picker sheet
                .build();

        // 2. Create the Request
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        // 3. Launch the Credential Manager (Async)
        credentialManager.getCredentialAsync(
                requireContext(),
                request,
                new android.os.CancellationSignal(),
                executor, // Run in background thread
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        // Handle Success on UI Thread
                        requireActivity().runOnUiThread(() -> handleSignIn(result));
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (!isAdded()) return;

                        requireActivity().runOnUiThread(() -> {
                            setLoading(false);

                            if (e instanceof NoCredentialException) {
                                showAddAccountSnackbar();
                            } else {
                                Log.e("Auth", "Credential Manager Error", e);
                                Toast.makeText(requireContext(), "Sign In Failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );
    }

    private void showAddAccountSnackbar() {
        // 1. Create the Snackbar
        Snackbar snackbar = Snackbar.make(binding.getRoot(), "No Google Account found", 5000);

        // 2. Customize the Background (Dark Pill Shape)
        View view = snackbar.getView();         // Remove default background
        view.setBackgroundTintList(null);    // Remove default dark tint
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28f * getResources().getDisplayMetrics().density); // Pill shape
        bg.setColor(ContextCompat.getColor(requireContext(), R.color.primary)); // Dark Grey background
        view.setBackground(bg);

        // 3. Customize Margins (Floating effect)
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        float d = getResources().getDisplayMetrics().density;

        TextView tv = view.findViewById(com.google.android.material.R.id.snackbar_text);
        Drawable googleIcon = getDrawable(requireContext(), R.drawable.ic_google);

        if (googleIcon != null) {
            // Size the icon to be small and neat (24dp)
            googleIcon.setBounds(0, 0, (int) (24 * d), (int) (24 * d));

            // Tint the icon to match the text (Dark Teal)
            DrawableCompat.setTintList(googleIcon, null);

            // Set the icon to the LEFT of the text
            // (left, top, right, bottom)
            tv.setCompoundDrawables(googleIcon, null, null, null);

            // Add space between Icon and Text (12dp)
            tv.setCompoundDrawablePadding((int) (12 * d));
        }

        TextView btnAction = view.findViewById(com.google.android.material.R.id.snackbar_action);
        Drawable arrowIcon = getDrawable(requireContext(), R.drawable.ic_arrow_right);

        if (arrowIcon != null) {
            // Size the arrow slightly smaller (e.g., 18dp) for a clean look
            int arrowSize = (int) (18 * d);
            arrowIcon.setBounds(0, 0, arrowSize, arrowSize);

            // TINT the arrow to match the text color
            androidx.core.graphics.drawable.DrawableCompat.setTint(arrowIcon, Color.WHITE);

            // Set to RIGHT of text (left, top, right, bottom)
            btnAction.setCompoundDrawables(null, null, arrowIcon, null);

            // Padding between "ADD ACCOUNT" and ">"
            btnAction.setCompoundDrawablePadding((int) (4 * d));
        }

        // Margins: Left=16dp, Top=0, Right=16dp, Bottom=24dp
        // (Reduced bottom margin since Login screen has no bottom nav)
        params.setMargins((int) (16 * d), 0, (int) (16 * d), (int) (24 * d));
        view.setLayoutParams(params);

        // 4. Customize Text Colors
        snackbar.setTextColor(Color.WHITE);
        snackbar.setActionTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary_soft));

        // 5. Set the Action
        snackbar.setAction("ADD ACCOUNT", v -> {
            openAddAccountSettings();
        });

        snackbar.show();
    }

    private void openAddAccountSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"});
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(requireContext(), "Please go to Settings -> Accounts", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSignIn(GetCredentialResponse result) {
        // 4. Parse the Result
        if (result.getCredential() instanceof CustomCredential) {
            CustomCredential credential = (CustomCredential) result.getCredential();

            // Check if it's a Google ID Token
            if (credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                try {
                    // Extract the ID Token
                    GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                    String idToken = googleIdTokenCredential.getIdToken();

                    // 5. Authenticate with Firebase
                    firebaseAuthWithGoogle(idToken);

                } catch (Exception e) {
                    // Changed from GoogleIdTokenParsingException to Exception
                    setLoading(false);
                    Log.e("Auth", "Invalid Google Token", e);
                    Toast.makeText(getContext(), "Invalid Google Token", Toast.LENGTH_SHORT).show();
                }
            } else {
                setLoading(false);
                Toast.makeText(getContext(), "Unexpected credential type", Toast.LENGTH_SHORT).show();
            }
        } else {
            setLoading(false);
            Toast.makeText(getContext(), "Unexpected credential type", Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        navigateToHome();
                    } else {
                        Toast.makeText(getContext(), "Firebase Auth Failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToHome() {

        setLoading(true);
        binding.btnAuthAction.setText("Restoring Data...");

        disposables.add(
                Single.fromCallable(() -> syncRepository.syncDataBlocking())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                success -> {
                                    NavController navController = Navigation.findNavController(requireView());
                                    try {
                                        navController.navigate(R.id.action_login_to_home);
                                    } catch (Exception e) {
                                        navController.navigate(R.id.homeFragment);
                                    }
                                    Toast.makeText(getContext(),
                                            success ? "Account Synced Successfully ✅" : "Welcome Back! (Offline Mode)",
                                            Toast.LENGTH_SHORT).show();
                                },
                                error -> {
                                    setLoading(false);
                                    Toast.makeText(getContext(), "Sync failed. Try again.", Toast.LENGTH_SHORT).show();
                                }
                        )
        );
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnAuthAction.setEnabled(!isLoading);
        binding.btnGoogle.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.toggleAuthMode.setEnabled(!isLoading);
    }
}