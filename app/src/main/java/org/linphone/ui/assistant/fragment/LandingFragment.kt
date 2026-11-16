/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * DzCentrex : écran de démarrage réduit à la configuration par QR code, lu par la caméra (écran du scanner de
 * Linphone) ou dans une image du téléphone (sélecteur de photos, LectureQrImage). Connexion linphone.org, création
 * et récupération de compte, saisie manuelle d'un compte SIP et aide Linphone retirées.
 */
package org.linphone.ui.assistant.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantLandingFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.viewmodel.AccountLoginViewModel
import org.linphone.ui.assistant.viewmodel.QrCodeViewModel

@UiThread
class LandingFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Landing Fragment]"
    }

    private lateinit var binding: AssistantLandingFragmentBinding

    private val viewModel: AccountLoginViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private val qrCodeViewModel: QrCodeViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private val choixImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            Log.i("$TAG No image chosen")
        } else {
            Log.i("$TAG Image chosen, looking for a QR code in it")
            qrCodeViewModel.lireImage(requireContext(), uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantLandingFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)
        observeToastEvents(qrCodeViewModel)

        binding.setBackClickListener {
            requireActivity().finish()
        }

        binding.setQrCodeClickListener {
            if (findNavController().currentDestination?.id == R.id.landingFragment) {
                val action =
                    LandingFragmentDirections.actionLandingFragmentToQrCodeScannerFragment()
                findNavController().navigate(action)
            }
        }

        binding.setImageClickListener {
            choixImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        qrCodeViewModel.lectureEnCours.observe(viewLifecycleOwner) {
            binding.lectureEnCours = it
        }

        // QR lu dans une image : même suite que depuis l'écran du scanner.
        qrCodeViewModel.remoteProvisioningSuccessfulEvent.observe(viewLifecycleOwner) {
            it.consume { atLeastOneAccountFound ->
                if (atLeastOneAccountFound) {
                    Log.i("$TAG Remote provisioning applied, leaving assistant")
                    requireActivity().finish()
                } else {
                    (requireActivity() as GenericActivity).showRedToast(
                        getString(R.string.assistant_qr_code_invalid_toast),
                        R.drawable.warning_circle
                    )
                }
            }
        }
    }
}
