package uz.imsoft.mijoz.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.data.LoginRepository
import uz.imsoft.mijoz.data.RefreshWorker
import uz.imsoft.mijoz.databinding.FragmentLoginBinding
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.describe
import uz.imsoft.mijoz.ui.settings.LinkDialog
import uz.imsoft.shared.LoginRecord

/**
 * Kirish oynasi: do'kon kodi + telefon + parol. Bir marta so'raladi — keyin ilova o'zi eslab qoladi
 * (1-ilova kalitni almashtirsa ham jimgina qayta kiradi). Muqobil: eski "ulash kodi".
 */
class LoginFragment : Fragment() {
    private var _b: FragmentLoginBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentLoginBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val p = Locator.prefs
        b.edtShop.setText(p.lastShopCode)
        if (b.edtPhone.text.isNullOrEmpty()) b.edtPhone.setText(if (p.loginPhone.isNotEmpty()) uz.imsoft.shared.Phone.pretty(p.loginPhone) else "+998 ")
        b.tvRevoked.visibility = if (p.revokedAt > 0 && p.link == null) View.VISIBLE else View.GONE
        b.btnLogin.setOnClickListener { submit() }
        b.edtPassword.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }
        b.btnCode.setOnClickListener { LinkDialog.show(this) { if (Locator.prefs.link != null) done() } }
        b.btnLater.setOnClickListener {
            Locator.prefs.skipLogin = true
            (activity as? MainActivity)?.closeLogin()
        }
    }

    private fun submit() {
        val shop = b.edtShop.text?.toString().orEmpty().trim()
        val phone = b.edtPhone.text?.toString().orEmpty()
        val pw = b.edtPassword.text?.toString().orEmpty().trim()
        b.tilShop.error = null; b.tilPhone.error = null; b.tilPassword.error = null
        if (shop.isEmpty()) { b.tilShop.error = getString(R.string.lgn_err_shop); return }
        if (!uz.imsoft.shared.Phone.isValid(phone)) { b.tilPhone.error = getString(R.string.lgn_err_phone); return }
        if (pw.length < LoginRecord.MIN_PASSWORD) { b.tilPassword.error = getString(R.string.lgn_err_password_short, LoginRecord.MIN_PASSWORD); return }
        busy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val g = Locator.login.login(shop, phone, pw)
                Locator.prefs.lastShopCode = shop
                Locator.prefs.revokedAt = 0L
                Locator.data.onLinked()
                RefreshWorker.schedule(requireContext().applicationContext, Locator.prefs.autoMinutes)
                runCatching { Locator.data.refresh(askFresh = true) }
                val who = g.memberName.ifBlank { g.clientName }
                activity?.let { a -> Snackbar.make(a.findViewById(android.R.id.content), getString(R.string.lgn_welcome, who), Snackbar.LENGTH_LONG).show() }
                done()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: LoginRepository.Failure.BadShopCode) {
                b.tilShop.error = getString(R.string.lgn_err_shop)
            } catch (e: LoginRepository.Failure.BadPhone) {
                b.tilPhone.error = getString(R.string.lgn_err_phone)
            } catch (e: LoginRepository.Failure.NoSuchPhone) {
                b.tilPhone.error = getString(R.string.lgn_err_no_phone)
            } catch (e: LoginRepository.Failure.WrongPassword) {
                b.tilPassword.error = getString(R.string.lgn_err_password)
            } catch (e: Exception) {
                _b?.let { Snackbar.make(it.root, getString(R.string.lgn_err_net) + "\n" + requireContext().describe(e), Snackbar.LENGTH_LONG).show() }
            } finally {
                busy(false)
            }
        }
    }

    private fun busy(v: Boolean) {
        val bb = _b ?: return
        bb.progress.visibility = if (v) View.VISIBLE else View.INVISIBLE
        bb.btnLogin.isEnabled = !v
        bb.edtShop.isEnabled = !v; bb.edtPhone.isEnabled = !v; bb.edtPassword.isEnabled = !v
    }

    private fun done() { (activity as? MainActivity)?.closeLogin() }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
