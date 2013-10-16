    /**
     * A dummy fragment containing a simple view.
     */
    public static class DummyFragment extends Fragment {
<#if hasSections?has_content>
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static DummyFragment newInstance(int sectionNumber) {
            DummyFragment fragment = new DummyFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }
        </#if>

        public DummyFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.${fragmentLayoutName}, container, false);
            <#if hasSections?has_content>
            TextView dummyTextView = (TextView) rootView.findViewById(R.id.section_label);
            dummyTextView.setText(Integer.toString(getArguments().getInt(ARG_SECTION_NUMBER)));
            </#if>
            return rootView;
        }
<#if navType == 'drawer'>

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((${activityClass}) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
</#if>
    }
